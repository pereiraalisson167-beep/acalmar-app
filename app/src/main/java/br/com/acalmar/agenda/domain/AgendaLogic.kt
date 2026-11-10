package br.com.acalmar.agenda.domain

import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.DayHours
import br.com.acalmar.agenda.data.TimeBlock
import java.time.LocalDate

// Regras da agenda, sem depender do Android (podem ser testadas isoladamente).

/** Um item da lista do dia: atendimento, horário bloqueado ou horário livre. */
sealed interface DayItem {
    val startMin: Int
    val endMin: Int

    data class Appt(val appt: Appointment) : DayItem {
        override val startMin: Int get() = appt.startMin
        override val endMin: Int get() = appt.startMin + appt.durationMin
    }

    /** `block` nulo significa o intervalo de almoço configurado. */
    data class Blocked(
        val block: TimeBlock?,
        val label: String,
        override val startMin: Int,
        override val endMin: Int,
        val allDay: Boolean = false
    ) : DayItem

    data class Free(override val startMin: Int, override val endMin: Int) : DayItem
}

fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean = aStart < bEnd && bStart < aEnd

private fun blockRange(block: TimeBlock): Pair<Int, Int> =
    if (block.allDay) 0 to DAY_MIN else block.startMin to block.endMin

private fun holdsSlot(appt: Appointment): Boolean =
    appt.status == ApptStatus.AGENDADO || appt.status == ApptStatus.CONCLUIDO

/**
 * Monta a lista do dia em ordem de horário: atendimentos (menos os desmarcados), bloqueios,
 * almoço e os intervalos livres dentro do expediente.
 *
 * @param hideFreeBefore não mostra horário livre antes deste minuto (para o dia de hoje).
 */
fun buildDay(
    day: DayHours,
    lunch: Pair<Int, Int>?,
    appts: List<Appointment>,
    blocks: List<TimeBlock>,
    hideFreeBefore: Int = 0,
    minFreeMin: Int = 15
): List<DayItem> {
    val fixed = mutableListOf<DayItem>()
    appts.filter { it.status != ApptStatus.DESMARCADO }.forEach { fixed += DayItem.Appt(it) }
    blocks.forEach { b ->
        val (s, e) = blockRange(b)
        fixed += DayItem.Blocked(b, b.reason, s, e, allDay = b.allDay)
    }
    if (lunch != null) fixed += DayItem.Blocked(null, "Almoço", lunch.first, lunch.second)

    val sorted = fixed.sortedWith(compareBy<DayItem>({ it.startMin }, { it.endMin }))
    if (!day.open) return sorted

    val frees = mutableListOf<DayItem>()
    var cursor = maxOf(day.startMin, hideFreeBefore)
    for (item in sorted) {
        if (cursor >= day.endMin) break
        // Faltas e itens já passados não ocupam mais o horário, mas continuam na lista.
        val occupies = when (item) {
            is DayItem.Appt -> holdsSlot(item.appt)
            else -> true
        }
        if (!occupies) continue
        if (item.startMin > cursor) {
            val freeEnd = minOf(item.startMin, day.endMin)
            if (freeEnd - cursor >= minFreeMin) frees += DayItem.Free(cursor, freeEnd)
        }
        cursor = maxOf(cursor, item.endMin)
    }
    if (day.endMin - cursor >= minFreeMin) frees += DayItem.Free(cursor, day.endMin)

    return (sorted + frees).sortedWith(
        compareBy<DayItem>({ it.startMin }, { if (it is DayItem.Free) 0 else 1 }, { it.endMin })
    )
}

/** Faixas de horário ocupadas no dia (atendimentos ativos, bloqueios e almoço). */
fun busyRanges(
    appts: List<Appointment>,
    blocks: List<TimeBlock>,
    lunch: Pair<Int, Int>?,
    excludeApptId: Long? = null
): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    appts.forEach { a ->
        if (a.id != excludeApptId && holdsSlot(a)) out += a.startMin to (a.startMin + a.durationMin)
    }
    blocks.forEach { out += blockRange(it) }
    if (lunch != null) out += lunch
    return out
}

/** Horários de início disponíveis para um atendimento com a duração dada. */
fun freeStarts(
    day: DayHours,
    slotMin: Int,
    durationMin: Int,
    busy: List<Pair<Int, Int>>,
    notBefore: Int = 0
): List<Int> {
    if (!day.open || slotMin <= 0 || durationMin <= 0) return emptyList()
    val out = mutableListOf<Int>()
    var t = day.startMin
    while (t + durationMin <= day.endMin) {
        if (t >= notBefore && busy.none { overlaps(t, t + durationMin, it.first, it.second) }) out += t
        t += slotMin
    }
    return out
}

data class Conflict(val what: String, val startMin: Int, val endMin: Int)

/** O que já existe no horário pretendido. Lista vazia = horário livre. */
fun findConflicts(
    startMin: Int,
    durationMin: Int,
    appts: List<Appointment>,
    blocks: List<TimeBlock>,
    lunch: Pair<Int, Int>?,
    clientNames: Map<Long, String>,
    excludeApptId: Long? = null
): List<Conflict> {
    val end = startMin + durationMin
    val out = mutableListOf<Conflict>()
    appts.forEach { a ->
        val aEnd = a.startMin + a.durationMin
        if (a.id != excludeApptId && holdsSlot(a) && overlaps(startMin, end, a.startMin, aEnd)) {
            out += Conflict(clientNames[a.clientId] ?: "Outro atendimento", a.startMin, aEnd)
        }
    }
    blocks.forEach { b ->
        val (s, e) = blockRange(b)
        if (overlaps(startMin, end, s, e)) {
            out += Conflict(if (b.reason.isBlank()) "Horário bloqueado" else "Bloqueio: ${b.reason}", s, e)
        }
    }
    if (lunch != null && overlaps(startMin, end, lunch.first, lunch.second)) {
        out += Conflict("Almoço", lunch.first, lunch.second)
    }
    return out
}

enum class RepeatEvery(val label: String) {
    NONE("Não repete"),
    WEEK("Toda semana"),
    TWO_WEEKS("A cada 2 semanas"),
    MONTH("Todo mês")
}

/** Datas de uma sequência de sessões que se repetem (a primeira é a própria data inicial). */
fun repeatDates(first: LocalDate, every: RepeatEvery, count: Int): List<LocalDate> {
    if (every == RepeatEvery.NONE || count <= 1) return listOf(first)
    return (0 until count).map { i ->
        when (every) {
            RepeatEvery.WEEK -> first.plusWeeks(i.toLong())
            RepeatEvery.TWO_WEEKS -> first.plusWeeks(2L * i)
            RepeatEvery.MONTH -> first.plusMonths(i.toLong())
            RepeatEvery.NONE -> first
        }
    }
}

/** Arredonda para cima até o próximo múltiplo de `step` (ex.: 09:17 com passo 30 -> 09:30). */
fun roundUpTo(min: Int, step: Int): Int = if (step <= 0) min else ((min + step - 1) / step) * step
