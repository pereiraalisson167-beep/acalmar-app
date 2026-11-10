package br.com.acalmar.agenda.domain

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

// Formatação em português do Brasil, escrita à mão para dar sempre o mesmo resultado
// em qualquer aparelho.

private val WEEKDAYS = listOf(
    "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"
)
private val WEEKDAYS_SHORT = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
private val MONTHS = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
)
private val MONTHS_SHORT = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

const val DAY_MIN = 24 * 60

// ---------- Dinheiro

/** 7500 -> "75,00"; 123456 -> "1.234,56". */
fun formatMoneyPlain(cents: Long): String {
    val value = abs(cents)
    val reais = (value / 100).toString()
    val grouped = reais.reversed().chunked(3).joinToString(".").reversed()
    val sign = if (cents < 0) "-" else ""
    return sign + grouped + "," + (value % 100).toString().padStart(2, '0')
}

/** 7500 -> "R$ 75,00". */
fun formatMoney(cents: Long): String = "R$ " + formatMoneyPlain(cents)

// ---------- Horas

/** 570 -> "09:30". */
fun formatTime(min: Int): String {
    if (min >= DAY_MIN) return "24:00"
    val m = min.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}

fun formatTimeRange(startMin: Int, endMin: Int): String = formatTime(startMin) + " – " + formatTime(endMin)

/** 45 -> "45 min"; 60 -> "1h"; 90 -> "1h30". */
fun formatDuration(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h" + (min % 60).toString().padStart(2, '0')
}

// ---------- Datas

fun epochToDate(day: Long): LocalDate = LocalDate.ofEpochDay(day)

fun weekdayName(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

fun weekdayShort(date: LocalDate): String = WEEKDAYS_SHORT[date.dayOfWeek.value - 1]

fun weekdayNameOfIndex(index: Int): String = WEEKDAYS[index]

fun monthName(date: LocalDate): String = MONTHS[date.monthValue - 1]

/** "10 de novembro". */
fun formatDayMonthLong(date: LocalDate): String = "${date.dayOfMonth} de ${monthName(date)}"

/** "10 de novembro de 2026". */
fun formatDateLong(date: LocalDate): String = "${formatDayMonthLong(date)} de ${date.year}"

/** "10/11/2026". */
fun formatDateShort(date: LocalDate): String =
    String.format(Locale.ROOT, "%02d/%02d/%04d", date.dayOfMonth, date.monthValue, date.year)

/** "10 nov". */
fun formatDayMonth(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS_SHORT[date.monthValue - 1]}"

/** "Novembro de 2026". */
fun formatMonthYear(date: LocalDate): String =
    monthName(date).replaceFirstChar { it.uppercase() } + " de " + date.year

fun relativeLabel(date: LocalDate, today: LocalDate): String? = when (date) {
    today -> "Hoje"
    today.plusDays(1) -> "Amanhã"
    today.minusDays(1) -> "Ontem"
    else -> null
}

/** "Hoje", "Amanhã" ou "Ter, 10 nov" (com o ano quando for outro ano). */
fun friendlyDate(date: LocalDate, today: LocalDate): String {
    relativeLabel(date, today)?.let { return it }
    val base = "${weekdayShort(date)}, ${formatDayMonth(date)}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

/** Domingo da semana em que a data cai (a semana começa no domingo). */
fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

// ---------- Telefone

fun digitsOnly(text: String): String = text.filter { it.isDigit() }

private fun nationalDigits(raw: String): String {
    var d = digitsOnly(raw).trimStart('0')
    if (d.length in 12..13 && d.startsWith("55")) d = d.drop(2)
    return d
}

/** "11998484660" -> "(11) 99848-4660". Mantém o texto original se não reconhecer o formato. */
fun formatPhone(raw: String): String {
    val d = nationalDigits(raw)
    return when (d.length) {
        11 -> "(${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
        10 -> "(${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
        9 -> "${d.substring(0, 5)}-${d.substring(5)}"
        8 -> "${d.substring(0, 4)}-${d.substring(4)}"
        else -> raw.trim()
    }
}

/** Número no formato do link do WhatsApp (55 + DDD + número) ou nulo se estiver incompleto. */
fun whatsappNumber(raw: String): String? {
    val d = nationalDigits(raw)
    return if (d.length in 10..11) "55$d" else null
}

// ---------- Nomes

/** Texto sem acentos e em minúsculas, para ordenar e buscar. */
fun sortKey(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)

/** "Maria Aparecida Silva" -> "MS". */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

fun firstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

// ---------- Aniversário

/** Aplica a máscara dd/mm/aaaa enquanto a pessoa digita. */
fun maskBirthday(text: String): String {
    val d = digitsOnly(text).take(8)
    return buildString {
        d.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

/** Aceita vazio, "dd/mm" ou "dd/mm/aaaa". */
fun isValidBirthday(text: String): Boolean {
    if (text.isBlank()) return true
    val d = digitsOnly(text)
    if (d.length != 4 && d.length != 8) return false
    val day = d.substring(0, 2).toInt()
    val month = d.substring(2, 4).toInt()
    if (month !in 1..12) return false
    val maxDay = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }
    if (day !in 1..maxDay) return false
    if (d.length == 8 && d.substring(4).toInt() !in 1900..2100) return false
    return true
}
