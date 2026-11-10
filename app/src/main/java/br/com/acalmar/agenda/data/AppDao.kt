package br.com.acalmar.agenda.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---- Clientes
    @Query("SELECT * FROM clients")
    fun observeClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeClient(id: Long): Flow<Client?>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClient(id: Long): Client?

    @Query("SELECT * FROM clients")
    suspend fun getClients(): List<Client>

    @Insert
    suspend fun insertClient(client: Client): Long

    @Insert
    suspend fun insertClients(list: List<Client>)

    @Update
    suspend fun updateClient(client: Client)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClient(id: Long)

    // ---- Serviços
    @Query("SELECT * FROM services ORDER BY sortOrder, id")
    fun observeServices(): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getService(id: Long): Service?

    @Query("SELECT * FROM services ORDER BY sortOrder, id")
    suspend fun getServices(): List<Service>

    @Insert
    suspend fun insertService(service: Service): Long

    @Insert
    suspend fun insertServices(list: List<Service>)

    @Update
    suspend fun updateService(service: Service)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteService(id: Long)

    // ---- Pacotes oferecidos por serviço
    @Query("SELECT * FROM package_offers ORDER BY sessions, id")
    fun observeOffers(): Flow<List<PackageOffer>>

    @Query("SELECT * FROM package_offers WHERE serviceId = :serviceId ORDER BY sessions, id")
    suspend fun getOffersFor(serviceId: Long): List<PackageOffer>

    @Query("SELECT * FROM package_offers")
    suspend fun getOffers(): List<PackageOffer>

    @Insert
    suspend fun insertOffers(list: List<PackageOffer>)

    @Query("DELETE FROM package_offers WHERE serviceId = :serviceId")
    suspend fun deleteOffersFor(serviceId: Long)

    // ---- Pacotes das clientes
    @Query("SELECT * FROM client_packages")
    fun observeClientPackages(): Flow<List<ClientPackage>>

    @Query("SELECT * FROM client_packages WHERE id = :id")
    suspend fun getClientPackage(id: Long): ClientPackage?

    @Query("SELECT * FROM client_packages")
    suspend fun getClientPackages(): List<ClientPackage>

    @Insert
    suspend fun insertClientPackage(pkg: ClientPackage): Long

    @Insert
    suspend fun insertClientPackages(list: List<ClientPackage>)

    @Update
    suspend fun updateClientPackage(pkg: ClientPackage)

    @Query("DELETE FROM client_packages WHERE id = :id")
    suspend fun deleteClientPackage(id: Long)

    // ---- Agendamentos
    @Query("SELECT * FROM appointments")
    fun observeAppointments(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE date BETWEEN :fromDay AND :toDay ORDER BY date, startMin, id")
    fun observeAppointmentsBetween(fromDay: Long, toDay: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE date BETWEEN :fromDay AND :toDay ORDER BY date, startMin, id")
    suspend fun getAppointmentsBetween(fromDay: Long, toDay: Long): List<Appointment>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointment(id: Long): Appointment?

    @Query("SELECT * FROM appointments WHERE seriesId = :seriesId ORDER BY date, startMin, id")
    suspend fun getSeries(seriesId: String): List<Appointment>

    @Query("SELECT * FROM appointments")
    suspend fun getAppointments(): List<Appointment>

    @Insert
    suspend fun insertAppointment(appointment: Appointment): Long

    @Insert
    suspend fun insertAppointments(list: List<Appointment>)

    @Update
    suspend fun updateAppointment(appointment: Appointment)

    @Update
    suspend fun updateAppointments(list: List<Appointment>)

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointment(id: Long)

    // ---- Bloqueios de horário
    @Query("SELECT * FROM blocks WHERE date BETWEEN :fromDay AND :toDay ORDER BY date, startMin, id")
    fun observeBlocksBetween(fromDay: Long, toDay: Long): Flow<List<TimeBlock>>

    @Query("SELECT * FROM blocks WHERE date BETWEEN :fromDay AND :toDay ORDER BY date, startMin, id")
    suspend fun getBlocksBetween(fromDay: Long, toDay: Long): List<TimeBlock>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getBlock(id: Long): TimeBlock?

    @Query("SELECT * FROM blocks")
    suspend fun getBlocks(): List<TimeBlock>

    @Insert
    suspend fun insertBlock(block: TimeBlock): Long

    @Insert
    suspend fun insertBlocks(list: List<TimeBlock>)

    @Update
    suspend fun updateBlock(block: TimeBlock)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long)

    // ---- Caixa
    @Query("SELECT * FROM cash_entries")
    fun observeCash(): Flow<List<CashEntry>>

    @Query("SELECT * FROM cash_entries WHERE date = :day ORDER BY createdAt, id")
    fun observeCashOn(day: Long): Flow<List<CashEntry>>

    @Query("SELECT * FROM cash_entries WHERE id = :id")
    suspend fun getCashEntry(id: Long): CashEntry?

    @Query("SELECT * FROM cash_entries")
    suspend fun getCash(): List<CashEntry>

    @Insert
    suspend fun insertCash(entry: CashEntry): Long

    @Insert
    suspend fun insertCashAll(list: List<CashEntry>)

    @Update
    suspend fun updateCash(entry: CashEntry)

    @Query("DELETE FROM cash_entries WHERE id = :id")
    suspend fun deleteCash(id: Long)

    @Query("DELETE FROM cash_entries WHERE appointmentId = :appointmentId")
    suspend fun deleteCashForAppointment(appointmentId: Long)

    // ---- Configurações
    @Query("SELECT * FROM settings")
    fun observeSettings(): Flow<List<Setting>>

    @Query("SELECT value FROM settings WHERE name = :name")
    suspend fun getSetting(name: String): String?

    @Query("SELECT * FROM settings")
    suspend fun getSettings(): List<Setting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: Setting)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(list: List<Setting>)

    // ---- Limpeza total (usada ao restaurar um backup)
    @Query("DELETE FROM cash_entries")
    suspend fun clearCash()

    @Query("DELETE FROM appointments")
    suspend fun clearAppointments()

    @Query("DELETE FROM blocks")
    suspend fun clearBlocks()

    @Query("DELETE FROM client_packages")
    suspend fun clearClientPackages()

    @Query("DELETE FROM package_offers")
    suspend fun clearOffers()

    @Query("DELETE FROM services")
    suspend fun clearServices()

    @Query("DELETE FROM clients")
    suspend fun clearClients()

    @Query("DELETE FROM settings")
    suspend fun clearSettings()
}
