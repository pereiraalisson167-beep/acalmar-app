package br.com.acalmar.agenda.data

import android.net.Uri
import androidx.room.withTransaction
import br.com.acalmar.agenda.domain.PackageState
import br.com.acalmar.agenda.domain.RecallItem
import br.com.acalmar.agenda.domain.Receivable
import br.com.acalmar.agenda.domain.computePackageStates
import br.com.acalmar.agenda.domain.computeRecalls
import br.com.acalmar.agenda.domain.computeReceivables
import br.com.acalmar.agenda.domain.sortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Pagamento informado ao concluir um atendimento ou vender um pacote. */
data class PaymentInput(val amountCents: Long, val method: PayMethod, val date: Long)

/**
 * Ponto único de acesso aos dados. As telas leem os `Flow`s (que se atualizam sozinhos quando
 * algo muda no banco) e chamam as funções `suspend` para gravar.
 */
class Repository(private val db: AppDatabase, val photoStore: PhotoStore) {

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

    val quotes: Flow<List<Quote>> = dao.observeQuotes()

    val business: Flow<BusinessInfo> =
        settings.map { BusinessInfo.parse(it[Keys.BUSINESS]) }.distinctUntilChanged()

    /** Perguntas da ficha de anamnese (modelo padrão enquanto não for alterado). */
    val anamnesisTemplate: Flow<List<AnamnesisQuestion>> =
        settings.map { AnamnesisModel.parseTemplate(it[Keys.ANAMNESIS_TEMPLATE]) }.distinctUntilChanged()

    /** Texto do termo de consentimento (texto padrão enquanto não for alterado). */
    val consentText: Flow<String> =
        settings.map { it[Keys.CONSENT_TEXT]?.takeIf { text -> text.isNotBlank() } ?: ConsentModel.DEFAULT_TEXT }
            .distinctUntilChanged()

    /** Avisos de atenção de cada cliente, tirados da ficha de anamnese. */
    val alertsByClient: Flow<Map<Long, List<String>>> =
        combine(dao.observeAnamneses(), anamnesisTemplate) { list, template ->
            list.associate { it.clientId to AnamnesisModel.alerts(template, AnamnesisModel.parseAnswers(it.answersJson)) }
                .filterValues { it.isNotEmpty() }
        }

    /** Clientes que estão na hora de chamar de volta, considerando `today` como o dia de hoje. */
    fun recalls(today: Long): Flow<List<RecallItem>> =
        combine(clients, appointments, services) { clientList, appts, serviceList ->
            computeRecalls(clientList, appts, serviceList, today)
        }

    fun client(id: Long): Flow<Client?> = dao.observeClient(id)

    fun anamnesis(clientId: Long): Flow<Anamnesis?> = dao.observeAnamnesis(clientId)

    fun consents(clientId: Long): Flow<List<Consent>> = dao.observeConsents(clientId)

    fun photos(clientId: Long): Flow<List<Photo>> = dao.observePhotos(clientId)

    fun quote(id: Long): Flow<Quote?> = dao.observeQuote(id)

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

    suspend fun getAnamnesis(clientId: Long): Anamnesis? = dao.getAnamnesis(clientId)

    suspend fun getQuote(id: Long): Quote? = dao.getQuote(id)

    suspend fun getBusiness(): BusinessInfo = BusinessInfo.parse(dao.getSetting(Keys.BUSINESS))

    suspend fun getAnamnesisTemplate(): List<AnamnesisQuestion> =
        AnamnesisModel.parseTemplate(dao.getSetting(Keys.ANAMNESIS_TEMPLATE))

    suspend fun getConsentText(): String =
        dao.getSetting(Keys.CONSENT_TEXT)?.takeIf { it.isNotBlank() } ?: ConsentModel.DEFAULT_TEXT

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

    /** Apaga a cliente e tudo o que é dela (agendamentos, pacotes, fichas e fotos). */
    suspend fun deleteClient(id: Long) {
        val files = dao.getPhotosOf(id).map { it.fileName }
        dao.deleteClient(id)
        withContext(Dispatchers.IO) { files.forEach { photoStore.delete(it) } }
    }

    // ---------- Hora de chamar de volta

    /** Esconde a cliente da lista até o dia indicado. */
    suspend fun snoozeRecall(clientId: Long, until: Long) = dao.setRecallSnooze(clientId, until)

    /** Não chamar desta vez: a cliente só volta à lista depois de um novo atendimento. */
    suspend fun dismissRecall(clientId: Long, lastDate: Long) = dao.setRecallDismissed(clientId, lastDate)

    // ---------- Ficha de anamnese

    suspend fun saveAnamnesis(clientId: Long, answers: Collection<AnamnesisAnswer>) =
        dao.putAnamnesis(Anamnesis(clientId, AnamnesisModel.answersJson(answers), System.currentTimeMillis()))

    suspend fun deleteAnamnesis(clientId: Long) = dao.deleteAnamnesis(clientId)

    suspend fun saveAnamnesisTemplate(questions: List<AnamnesisQuestion>) =
        dao.putSetting(Setting(Keys.ANAMNESIS_TEMPLATE, AnamnesisModel.templateJson(questions)))

    /** Volta para as perguntas padrão. */
    suspend fun resetAnamnesisTemplate() = dao.deleteSetting(Keys.ANAMNESIS_TEMPLATE)

    // ---------- Termo de consentimento

    suspend fun saveConsentText(text: String) = dao.putSetting(Setting(Keys.CONSENT_TEXT, text.trim()))

    /** Volta para o texto padrão. */
    suspend fun resetConsentText() = dao.deleteSetting(Keys.CONSENT_TEXT)

    suspend fun signConsent(consent: Consent): Long =
        dao.insertConsent(consent.copy(id = 0, createdAt = System.currentTimeMillis()))

    suspend fun deleteConsent(id: Long) = dao.deleteConsent(id)

    // ---------- Fotos

    /** Copia a imagem para dentro do app e registra a foto na ficha da cliente. */
    suspend fun addPhoto(clientId: Long, source: Uri, kind: PhotoKind, takenOn: Long, note: String): Long {
        val name = withContext(Dispatchers.IO) { photoStore.importFrom(source) }
        return try {
            dao.insertPhoto(
                Photo(
                    clientId = clientId,
                    fileName = name,
                    takenOn = takenOn,
                    kind = kind,
                    note = note.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            photoStore.delete(name)
            throw e
        }
    }

    suspend fun updatePhoto(photo: Photo) = dao.updatePhoto(photo)

    suspend fun deletePhoto(photo: Photo) {
        dao.deletePhoto(photo.id)
        withContext(Dispatchers.IO) { photoStore.delete(photo.fileName) }
    }

    // ---------- Orçamentos

    suspend fun saveQuote(quote: Quote): Long =
        if (quote.id == 0L) {
            dao.insertQuote(quote.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.updateQuote(quote)
            quote.id
        }

    suspend fun deleteQuote(id: Long) = dao.deleteQuote(id)

    // ---------- Dados do negócio

    suspend fun saveBusiness(info: BusinessInfo) = dao.putSetting(Setting(Keys.BUSINESS, info.toJson()))

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

    /** Marca como atendido, guarda a anotação da sessão e, se houver pagamento, lança no caixa. */
    suspend fun conclude(appointment: Appointment, payment: PaymentInput?, evolution: String? = null) {
        db.withTransaction {
            dao.updateAppointment(
                appointment.copy(
                    status = ApptStatus.CONCLUIDO,
                    evolution = evolution?.trim() ?: appointment.evolution
                )
            )
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

    /** Anotação de evolução da sessão (o que foi feito e como a cliente respondeu). */
    suspend fun setEvolution(appointmentId: Long, text: String) = dao.setEvolution(appointmentId, text.trim())

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

    private suspend fun snapshot(): BackupFile =
        db.withTransaction {
            BackupFile(
                app = BackupFile.APP_TAG,
                schema = BackupFile.CURRENT_SCHEMA,
                exportedAt = System.currentTimeMillis(),
                clients = dao.getClients(),
                services = dao.getServices(),
                offers = dao.getOffers(),
                clientPackages = dao.getClientPackages(),
                appointments = dao.getAppointments(),
                blocks = dao.getBlocks(),
                cash = dao.getCash(),
                settings = dao.getSettings(),
                anamneses = dao.getAnamneses(),
                consents = dao.getConsents(),
                photos = dao.getPhotos(),
                quotes = dao.getQuotes()
            )
        }

    /**
     * Grava o backup: um arquivo .zip com os dados (dados.json) e as fotos das clientes.
     * Devolve o resumo do que foi gravado.
     */
    suspend fun exportBackup(out: OutputStream): BackupSummary {
        val file = snapshot()
        var photoCount = 0
        withContext(Dispatchers.IO) {
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupFile.DATA_ENTRY))
                zip.write(file.toJson().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                file.photos.map { it.fileName }.distinct().forEach { name ->
                    val source = photoStore.file(name)
                    if (source.isFile) {
                        zip.putNextEntry(ZipEntry(BackupFile.PHOTO_DIR + source.name))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        photoCount++
                    }
                }
            }
        }
        return BackupSummary(file.clients.size, file.appointments.size, file.cash.size, photoCount)
    }

    suspend fun markBackupDone() =
        dao.putSetting(Setting(Keys.LAST_BACKUP, System.currentTimeMillis().toString()))

    /**
     * Lê um arquivo de backup: o .zip atual ou o .json da primeira versão do app.
     * Quando `photoDir` é informado, as fotos do arquivo são extraídas para essa pasta.
     */
    private fun readBackupFile(raw: InputStream, photoDir: File?): BackupFile {
        val input = BufferedInputStream(raw)
        input.mark(4)
        val head = ByteArray(4)
        val read = input.read(head)
        input.reset()
        val isZip = read == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte()
        if (!isZip) return BackupFile.parse(input.readBytes().toString(Charsets.UTF_8))

        var json: String? = null
        val zip = ZipInputStream(input)
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name == BackupFile.DATA_ENTRY) {
                json = zip.readBytes().toString(Charsets.UTF_8)
            } else if (photoDir != null && !entry.isDirectory && name.startsWith(BackupFile.PHOTO_DIR)) {
                // Só o nome final é usado, para o arquivo nunca ser gravado fora da pasta.
                val fileName = File(name).name
                if (PHOTO_NAME.matches(fileName)) {
                    File(photoDir, fileName).outputStream().use { zip.copyTo(it) }
                }
            }
            entry = zip.nextEntry
        }
        return BackupFile.parse(json ?: throw IllegalArgumentException("O arquivo escolhido não é um backup do Acalmar."))
    }

    /** Confere o arquivo e devolve o que ele contém, sem alterar nada no app. */
    suspend fun inspectBackup(open: () -> InputStream): BackupSummary =
        withContext(Dispatchers.IO) {
            val file = open().use { readBackupFile(it, null) }
            BackupSummary(file.clients.size, file.appointments.size, file.cash.size, file.photos.size)
        }

    /** Substitui todos os dados do app pelos do arquivo. Se o arquivo for inválido, nada é alterado. */
    suspend fun importBackup(open: () -> InputStream): BackupSummary {
        val temp = File(photoStore.cacheDir, "restore")
        try {
            val file = withContext(Dispatchers.IO) {
                temp.deleteRecursively()
                temp.mkdirs()
                open().use { readBackupFile(it, temp) }
            }
            replaceAll(file)
            val restored = withContext(Dispatchers.IO) {
                val wanted = file.photos.map { it.fileName }.toSet()
                photoStore.deleteAllExcept(emptySet())
                var count = 0
                temp.listFiles()?.forEach { source ->
                    if (source.name in wanted) {
                        source.copyTo(photoStore.file(source.name), overwrite = true)
                        count++
                    }
                }
                count
            }
            return BackupSummary(file.clients.size, file.appointments.size, file.cash.size, restored)
        } finally {
            withContext(Dispatchers.IO) { temp.deleteRecursively() }
        }
    }

    private suspend fun replaceAll(file: BackupFile) {
        db.withTransaction {
            dao.clearCash()
            dao.clearAppointments()
            dao.clearBlocks()
            dao.clearClientPackages()
            dao.clearOffers()
            dao.clearQuotes()
            dao.clearPhotos()
            dao.clearConsents()
            dao.clearAnamneses()
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
            dao.insertAnamneses(file.anamneses)
            dao.insertConsents(file.consents)
            dao.insertPhotos(file.photos)
            dao.insertQuotes(file.quotes)
            dao.putSettings(file.settings)
            dao.putSetting(Setting(Keys.SEEDED, "1"))
        }
    }

    private fun joinTitle(title: String, clientName: String): String =
        listOf(title, clientName).filter { it.isNotBlank() }.joinToString(" · ")

    companion object {
        private val PHOTO_NAME = Regex("[A-Za-z0-9._-]{1,80}")

        fun packageTitle(pkg: ClientPackage): String =
            "Pacote ${pkg.serviceName} (${pkg.totalSessions} sessões)"
    }
}
