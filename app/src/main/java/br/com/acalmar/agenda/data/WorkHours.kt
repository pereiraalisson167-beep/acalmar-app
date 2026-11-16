package br.com.acalmar.agenda.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Json usado no backup e nas configurações guardadas como texto. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Nomes das configurações guardadas na tabela "settings". */
object Keys {
    const val SEEDED = "seeded"
    const val WORK_HOURS = "work_hours"
    const val HOURS_CONFIGURED = "hours_configured"
    const val LAST_BACKUP = "last_backup_at"
    const val BUSINESS = "business"
    const val ANAMNESIS_TEMPLATE = "anamnesis_template"
    const val CONSENT_TEXT = "consent_text"

    /** Pasta escolhida para o backup automático (endereço dado pelo seletor de pastas do Android). */
    const val AUTO_BACKUP_FOLDER = "auto_backup_folder"
    const val AUTO_BACKUP_FOLDER_NAME = "auto_backup_folder_name"

    /** Momento do último backup automático gravado com sucesso. */
    const val AUTO_BACKUP_AT = "auto_backup_at"

    /** Impressão digital dos dados na última cópia automática (para só copiar quando algo muda). */
    const val AUTO_BACKUP_HASH = "auto_backup_hash"

    /** Mensagem do último erro do backup automático (vazia quando deu certo). */
    const val AUTO_BACKUP_ERROR = "auto_backup_error"
}

@Serializable
data class DayHours(
    val open: Boolean = true,
    val startMin: Int = 8 * 60,
    val endMin: Int = 19 * 60
)

/** Horário de atendimento. `days` tem 7 posições: 0 = segunda ... 6 = domingo. */
@Serializable
data class WorkHours(
    val days: List<DayHours> = List(7) { DayHours(open = it < 6) },
    val lunchEnabled: Boolean = false,
    val lunchStart: Int = 12 * 60,
    val lunchEnd: Int = 13 * 60,
    /** De quanto em quanto tempo os horários são oferecidos ao agendar. */
    val slotMin: Int = 30
) {
    fun forDate(date: LocalDate): DayHours =
        days.getOrNull(date.dayOfWeek.value - 1) ?: DayHours(open = false)

    /** Intervalo de almoço daquele dia, ou nulo se não houver. */
    fun lunchFor(day: DayHours): Pair<Int, Int>? =
        if (day.open && lunchEnabled && lunchEnd > lunchStart) lunchStart to lunchEnd else null

    fun toJson(): String = AppJson.encodeToString(serializer(), this)

    companion object {
        fun parse(text: String?): WorkHours {
            if (text.isNullOrBlank()) return WorkHours()
            return runCatching { AppJson.decodeFromString(serializer(), text) }
                .getOrNull()
                ?.takeIf { it.days.size == 7 }
                ?: WorkHours()
        }
    }
}
