package br.com.acalmar.agenda.data

import androidx.room.withTransaction
import br.com.acalmar.agenda.domain.PackageState
import br.com.acalmar.agenda.domain.Receivable
import br.com.acalmar.agenda.domain.computePackageStates
import br.com.acalmar.agenda.domain.computeReceivables
import br.com.acalmar.agenda.domain.sortKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Pagamento informado ao concluir um atendimento ou vender um pacote. */
data class PaymentInput(val amountCents: Long, val method: PayMethod, val date: Long)

/**
 * Ponto único de acesso aos dados. As telas leem os `Flow`s (que se atualizam sozinhos quando
 * algo muda no banco) e chamam as funções `suspend` para gravar.
 */
class Repository(private val db: AppDatabase) {

    private val dao = db.dao()

    // ---------- Leitura

    val clients: Flow<List<Client>> =
        dao.observeClients().map { list -> list.sortedBy { sortKey(it.name) } }

    val services: Flow<List<Service>> = dao.observeServices()

    val offers: Flow<List<PackageOffer>> = dao.observeOffers()

    val appointments: Flow<List<Appointment>> = dao.observeAppointments()

    val cash: Flow<List<CashEntry>> = dao.observeCash()

    val settings: Flow<Map<String, String>> =
        dao.observeSettings().map { list -> list.associate { it.name to it.value } }

    val workHours: Flow<WorkHours> =
        settings.map { WorkHours.parse(it[Keys.WORK_HOURS]) }.distinctUntilChanged()

    val packageStates: Flow<List<PackageState>> =
        combine(dao.observeClientPackages(), appointments, cash) { packages, appts, entries ->
            computePackageStates(packages, appts, entries)
        }

    val receivables: Flow<List<Receivable>> =
        combine(appointments, packageStates, cash) { appts, states, entries ->
            computeReceivables(appts, states, entries)
        }

    fun client(id: Long): Flow<Client?> = dao.observeClient(id)

    fun appointmentsBetween(fromDay: Long, toDay: Long): Flow<List<Appointment>> =
        dao.observeAppointmentsBetween(fromDay, toDay)

    fun blocksBetween(fromDay: Long, toDay: Long): Flow<List<TimeBlock>> =
        dao.observeBlocksBetween(fromDay, toDay)

    fun cashOn(day: Long): Flow<List<CashEntry>> = dao.observeCashOn(day)

    suspend fun getClient(id: Long): Client? = dao.getClient(id)

    suspend fun getService(id: Long): Service? = dao.getService(id)

    suspend fun getOffersFor(serviceId: Long): List<PackageOffer> = dao.getOffersFor(serviceId)

    suspend fun getAppointment(id: Long): Appointment? = dao.getAppointment(id)

    suspend fun getBlock(id: Long): TimeBlock? = dao.getBlock(id)

    suspend fun getCashEntry(id: Long): CashEntry? = dao.getCashEntry(id)

    suspend fun getClientPackage(id: Long): ClientPackage? = dao.getClientPackage(id)

    suspend fun getWorkHours(): WorkHours = WorkHours.parse(dao.getSetting(Keys.WORK_HOURS))

    suspend fun appointmentsOn(day: Long): List<Appointment> = dao.getAppointmentsBetween(day, day)

    suspend fun blocksOn(day: Long): List<TimeBlock> = dao.getBlocksBetween(day, day)

    // ---------- Primeira abertura

    suspend fun ensureSeeded() {
        if (dao.getSetting(Keys.SEEDED) != null) return
        db.withTransaction {
            if (dao.getServices().isEmpty()) Seed.insert(dao)
            if (dao.getSetting(Keys.WORK_HOURS) == null) {
                dao.putSetting(Setting(Keys.WORK_HOURS, WorkHours().toJson()))
            }
            dao.putSetting(Setting(Keys.SEEDED, "1"))
        }
    }

    // ---------- Clientes

    suspend fun saveClient(client: Client): Long =
        if (client.id == 0L) {
            dao.insertClient(client.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.updateClient(client)
            client.id
        }

    suspend fun deleteClient(id: Long) = dao.deleteClient(id)

    // ---------- Serviços e pacotes oferecidos

    suspend fun saveService(service: Service, offers: List<PackageOffer>): Long =
        db.withTransaction {
            val id = if (service.id == 0L) {
                dao.insertService(service)
            } else {
                dao.updateService(service)
                service.id
            }
            dao.deleteOffersFor(id)
            dao.insertOffers(offers.map { it.copy(id = 0, serviceId = id) })
            id
        }

    suspend fun deleteService(id: Long) = dao.deleteService(id)

    // ---------- Agendamentos

    suspend fun createAppointments(list: List<Appointment>) {
        val now = System.currentTimeMillis()
        dao.insertAppointments(list.map { it.copy(id = 0, createdAt = now) })
    }

    suspend fun updateAppointment(appointment: Appointment) = dao.updateAppointment(appointment)

    /** Este atendimento e os seguintes da mesma série que ainda estão agendados. */
    private suspend fun thisAndFollowing(appointment: Appointment): List<Appointment> {
        val seriesId = appointment.seriesId ?: return listOf(appointment)
        return dao.getSeries(seriesId).filter {
            it.id == appointment.id || (it.date > appointment.date && it.status == ApptStatus.AGENDADO)
        }
    }

    /** Quantos atendimentos futuros da mesma série ainda estão agendados (sem contar este). */
    suspend fun countFollowing(appointment: Appointment): Int = thisAndFollowing(appointment).size - 1

    /** A cliente avisou que não vem: o horário fica livre e nada é descontado do pacote. */
    suspend fun unschedule(appointment: Appointment, andFollowing: Boolean) {
        db.withTransaction {
            val targets = if (andFollowing) thisAndFollowing(appointment) else listOf(appointment)
            dao.updateAppointments(targets.map { it.copy(status = ApptStatus.DESMARCADO) })
        }
    }

    suspend fun deleteAppointment(appointment: Appointment, andFollowing: Boolean) {
        db.withTransaction {
            val targets = if (andFollowing) thisAndFollowing(appointment) else listOf(appointment)
            targets.forEach { dao.deleteAppointment(it.id) }
        }
    }

    /** Marca como atendido e, se houver pagamento, lança no caixa. */
    suspend fun conclude(appointment: Appointment, payment: PaymentInput?) {
        db.withTransaction {
            dao.updateAppointment(appointment.copy(status = ApptStatus.CONCLUIDO))
            if (payment != null && payment.amountCents > 0) {
                val clientName = dao.getClient(appointment.clientId)?.name.orEmpty()
                dao.insertCash(
                    CashEntry(
                        date = payment.date,
                        kind = CashKind.ENTRADA,
                        amountCents = payment.amountCents,
                        method = payment.method,
                        description = joinTitle(appointment.serviceName, clientName),
                        clientId = appointment.clientId,
                        appointmentId = appointment.id,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /** Falta: `chargePackage` diz se a sessão é descontada do pacote. */
    suspend fun markNoShow(appointment: Appointment, chargePackage: Boolean) =
        dao.updateAppointment(appointment.copy(status = ApptStatus.FALTOU, chargesPackage = chargePackage))

    /** Volta o atendimento para "agendado" e remove do caixa o pagamento ligado a ele. */
    suspend fun reopen(appointment: Appointment) {
        db.withTransaction {
            dao.deleteCashForAppointment(appointment.id)
            dao.updateAppointment(appointment.copy(status = ApptStatus.AGENDADO, chargesPackage = true))
        }
    }

    // ---------- Bloqueios

    suspend fun saveBlock(block: TimeBlock): Long =
        if (block.id == 0L) {
            dao.insertBlock(block)
        } else {
            dao.updateBlock(block)
            block.id
        }

    suspend fun deleteBlock(id: Long) = dao.deleteBlock(id)

    // ---------- Pacotes das clientes

    suspend fun sellPackage(pkg: ClientPackage, payment: PaymentInput?): Long =
        db.withTransaction {
            val id = dao.insertClientPackage(pkg.copy(id = 0))
            if (payment != null && payment.amountCents > 0) {
                val clientName = dao.getClient(pkg.clientId)?.name.orEmpty()
                dao.insertCash(
                    CashEntry(
                        date = payment.date,
                        kind = CashKind.ENTRADA,
                        amountCents = payment.amountCents,
                        method = payment.method,
                        description = joinTitle(packageTitle(pkg), clientName),
                        clientId = pkg.clientId,
                        clientPackageId = id,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            id
        }

    suspend fun updateClientPackage(pkg: ClientPackage) = dao.updateClientPackage(pkg)

    suspend fun deleteClientPackage(id: Long) = dao.deleteClientPackage(id)

    // ---------- Caixa

    suspend fun saveCash(entry: CashEntry): Long =
        if (entry.id == 0L) {
            dao.insertCash(entry.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.updateCash(entry)
            entry.id
        }

    suspend fun deleteCash(id: Long) = dao.deleteCash(id)

    /** Recebe (total ou parcialmente) um valor pendente. */
    suspend fun receive(receivable: Receivable, amountCents: Long, method: PayMethod, date: Long) {
        if (amountCents <= 0) return
        val clientName = dao.getClient(receivable.clientId)?.name.orEmpty()
        dao.insertCash(
            CashEntry(
                date = date,
                kind = CashKind.ENTRADA,
                amountCents = amountCents,
                method = method,
                description = joinTitle(receivable.title, clientName),
                clientId = receivable.clientId,
                appointmentId = receivable.appointmentId,
                clientPackageId = receivable.clientPackageId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // ---------- Configurações

    suspend fun saveWorkHours(hours: WorkHours) =
        dao.putSettings(
            listOf(
                Setting(Keys.WORK_HOURS, hours.toJson()),
                Setting(Keys.HOURS_CONFIGURED, "1")
            )
        )

    suspend fun setSetting(name: String, value: String) = dao.putSetting(Setting(name, value))

    // ---------- Backup

    suspend fun exportBackup(): String =
        db.withTransaction {
            BackupFile(
                app = BackupFile.APP_TAG,
                exportedAt = System.currentTimeMillis(),
                clients = dao.getClients(),
                services = dao.getServices(),
                offers = dao.getOffers(),
                clientPackages = dao.getClientPackages(),
                appointments = dao.getAppointments(),
                blocks = dao.getBlocks(),
                cash = dao.getCash(),
                settings = dao.getSettings()
            ).toJson()
        }

    suspend fun markBackupDone() =
        dao.putSetting(Setting(Keys.LAST_BACKUP, System.currentTimeMillis().toString()))

    /** Substitui todos os dados do app pelos do arquivo. Se algo der errado, nada é alterado. */
    suspend fun importBackup(text: String): BackupSummary {
        val file = BackupFile.parse(text)
        db.withTransaction {
            dao.clearCash()
            dao.clearAppointments()
            dao.clearBlocks()
            dao.clearClientPackages()
            dao.clearOffers()
            dao.clearServices()
            dao.clearClients()
            dao.clearSettings()

            dao.insertClients(file.clients)
            dao.insertServices(file.services)
            dao.insertOffers(file.offers)
            dao.insertClientPackages(file.clientPackages)
            dao.insertAppointments(file.appointments)
            dao.insertBlocks(file.blocks)
            dao.insertCashAll(file.cash)
            dao.putSettings(file.settings)
            dao.putSetting(Setting(Keys.SEEDED, "1"))
        }
        return BackupSummary(file.clients.size, file.appointments.size, file.cash.size)
    }

    private fun joinTitle(title: String, clientName: String): String =
        listOf(title, clientName).filter { it.isNotBlank() }.joinToString(" · ")

    companion object {
        fun packageTitle(pkg: ClientPackage): String =
            "Pacote ${pkg.serviceName} (${pkg.totalSessions} sessões)"
    }
}
