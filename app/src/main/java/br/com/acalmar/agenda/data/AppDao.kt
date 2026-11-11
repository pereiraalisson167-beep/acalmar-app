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

    @Query("UPDATE clients SET recallSnoozeUntil = :until WHERE id = :id")
    suspend fun setRecallSnooze(id: Long, until: Long?)

    @Query("UPDATE clients SET recallDismissedFor = :date WHERE id = :id")
    suspend fun setRecallDismissed(id: Long, date: Long?)

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

    @Query("UPDATE appointments SET evolution = :text WHERE id = :id")
    suspend fun setEvolution(id: Long, text: String)

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

    @Query("DELETE FROM settings WHERE name = :name")
    suspend fun deleteSetting(name: String)

    // ---- Fichas de anamnese
    @Query("SELECT * FROM anamneses WHERE clientId = :clientId")
    fun observeAnamnesis(clientId: Long): Flow<Anamnesis?>

    @Query("SELECT * FROM anamneses")
    fun observeAnamneses(): Flow<List<Anamnesis>>

    @Query("SELECT * FROM anamneses WHERE clientId = :clientId")
    suspend fun getAnamnesis(clientId: Long): Anamnesis?

    @Query("SELECT * FROM anamneses")
    suspend fun getAnamneses(): List<Anamnesis>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAnamnesis(anamnesis: Anamnesis)

    @Insert
    suspend fun insertAnamneses(list: List<Anamnesis>)

    @Query("DELETE FROM anamneses WHERE clientId = :clientId")
    suspend fun deleteAnamnesis(clientId: Long)

    // ---- Termos de consentimento assinados
    @Query("SELECT * FROM consents WHERE clientId = :clientId ORDER BY signedOn DESC, id DESC")
    fun observeConsents(clientId: Long): Flow<List<Consent>>

    @Query("SELECT * FROM consents")
    suspend fun getConsents(): List<Consent>

    @Insert
    suspend fun insertConsent(consent: Consent): Long

    @Insert
    suspend fun insertConsents(list: List<Consent>)

    @Query("DELETE FROM consents WHERE id = :id")
    suspend fun deleteConsent(id: Long)

    // ---- Fotos
    @Query("SELECT * FROM photos WHERE clientId = :clientId ORDER BY takenOn DESC, id DESC")
    fun observePhotos(clientId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE clientId = :clientId")
    suspend fun getPhotosOf(clientId: Long): List<Photo>

    @Query("SELECT * FROM photos")
    suspend fun getPhotos(): List<Photo>

    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    @Insert
    suspend fun insertPhotos(list: List<Photo>)

    @Update
    suspend fun updatePhoto(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    // ---- Orçamentos
    @Query("SELECT * FROM quotes ORDER BY createdOn DESC, id DESC")
    fun observeQuotes(): Flow<List<Quote>>

    @Query("SELECT * FROM quotes WHERE id = :id")
    fun observeQuote(id: Long): Flow<Quote?>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getQuote(id: Long): Quote?

    @Query("SELECT * FROM quotes")
    suspend fun getQuotes(): List<Quote>

    @Insert
    suspend fun insertQuote(quote: Quote): Long

    @Insert
    suspend fun insertQuotes(list: List<Quote>)

    @Update
    suspend fun updateQuote(quote: Quote)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteQuote(id: Long)

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

    @Query("DELETE FROM anamneses")
    suspend fun clearAnamneses()

    @Query("DELETE FROM consents")
    suspend fun clearConsents()

    @Query("DELETE FROM photos")
    suspend fun clearPhotos()

    @Query("DELETE FROM quotes")
    suspend fun clearQuotes()
}
