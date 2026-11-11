package br.com.acalmar.agenda.data

import kotlinx.serialization.Serializable

/**
 * Dados do backup. O arquivo de backup é um .zip com este conteúdo em "dados.json" e as fotos
 * das clientes na pasta "fotos/". A primeira versão do app gravava só o JSON, sem zip; esse
 * formato antigo continua sendo aceito na restauração.
 */
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
    val settings: List<Setting> = emptyList(),
    val anamneses: List<Anamnesis> = emptyList(),
    val consents: List<Consent> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val quotes: List<Quote> = emptyList()
) {
    fun toJson(): String = AppJson.encodeToString(serializer(), this)

    companion object {
        const val APP_TAG = "acalmar"
        const val CURRENT_SCHEMA = 2
        const val DATA_ENTRY = "dados.json"
        const val PHOTO_DIR = "fotos/"

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

data class BackupSummary(val clients: Int, val appointments: Int, val cashEntries: Int, val photos: Int = 0)
