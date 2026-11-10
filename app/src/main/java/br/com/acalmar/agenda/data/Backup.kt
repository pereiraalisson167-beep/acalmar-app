package br.com.acalmar.agenda.data

import kotlinx.serialization.Serializable

/** Conteúdo do arquivo de backup: todos os dados do app em um único arquivo de texto (JSON). */
@Serializable
data class BackupFile(
    /** Marca obrigatória: impede que um arquivo qualquer seja aceito como backup. */
    val app: String,
    val schema: Int = 1,
    val exportedAt: Long = 0,
    val clients: List<Client> = emptyList(),
    val services: List<Service> = emptyList(),
    val offers: List<PackageOffer> = emptyList(),
    val clientPackages: List<ClientPackage> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val blocks: List<TimeBlock> = emptyList(),
    val cash: List<CashEntry> = emptyList(),
    val settings: List<Setting> = emptyList()
) {
    fun toJson(): String = AppJson.encodeToString(serializer(), this)

    companion object {
        const val APP_TAG = "acalmar"

        /** Lê um arquivo de backup; lança erro se o arquivo não for um backup válido. */
        fun parse(text: String): BackupFile {
            val file = try {
                AppJson.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                throw IllegalArgumentException("O arquivo escolhido não é um backup do Acalmar.", e)
            }
            require(file.app == APP_TAG) { "O arquivo escolhido não é um backup do Acalmar." }
            return file
        }
    }
}

data class BackupSummary(val clients: Int, val appointments: Int, val cashEntries: Int)
