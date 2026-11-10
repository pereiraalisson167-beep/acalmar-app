package br.com.acalmar.agenda

import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.DayHours
import br.com.acalmar.agenda.data.TimeBlock
import br.com.acalmar.agenda.domain.DayItem
import br.com.acalmar.agenda.domain.RepeatEvery
import br.com.acalmar.agenda.domain.buildDay
import br.com.acalmar.agenda.domain.busyRanges
import br.com.acalmar.agenda.domain.findConflicts
import br.com.acalmar.agenda.domain.freeStarts
import br.com.acalmar.agenda.domain.repeatDates
import br.com.acalmar.agenda.domain.roundUpTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaLogicTest {

    // Expediente das 08:00 às 18:00.
    private val day = DayHours(open = true, startMin = 8 * 60, endMin = 18 * 60)

    private fun appt(
        id: Long,
        start: Int,
        duration: Int = 60,
        status: ApptStatus = ApptStatus.AGENDADO,
        client: Long = 1
    ) = Appointment(
        id = id,
        clientId = client,
        serviceName = "Massagem relaxante",
        date = 0,
        startMin = start,
        durationMin = duration,
        priceCents = 7500,
        status = status
    )

    private fun describe(items: List<DayItem>): List<String> = items.map {
        when (it) {
            is DayItem.Free -> "livre ${it.startMin}-${it.endMin}"
            is DayItem.Appt -> "atendimento ${it.appt.id}"
            is DayItem.Blocked -> "bloqueio ${it.startMin}-${it.endMin}"
        }
    }

    @Test
    fun emptyDayIsOneFreeBlock() {
        assertEquals(listOf("livre 480-1080"), describe(buildDay(day, null, emptyList(), emptyList())))
    }

    @Test
    fun freeTimeSurroundsAppointmentsAndLunch() {
        val items = buildDay(
            day = day,
            lunch = (12 * 60 + 30) to (13 * 60 + 30),
            appts = listOf(appt(1, 9 * 60), appt(2, 11 * 60, duration = 90)),
            blocks = emptyList()
        )
        assertEquals(
            listOf(
                "livre 480-540",
                "atendimento 1",
                "livre 600-660",
                "atendimento 2",
                "bloqueio 750-810",
                "livre 810-1080"
            ),
            describe(items)
        )
    }

    @Test
    fun unscheduledAppointmentFreesTheSlot() {
        val items = buildDay(day, null, listOf(appt(1, 9 * 60, status = ApptStatus.DESMARCADO)), emptyList())
        assertEquals(listOf("livre 480-1080"), describe(items))
    }

    @Test
    fun closedDayShowsAppointmentsButNoFreeTime() {
        val items = buildDay(DayHours(open = false), null, listOf(appt(1, 9 * 60)), emptyList())
        assertEquals(listOf("atendimento 1"), describe(items))
    }

    @Test
    fun todayHidesFreeTimeThatAlreadyPassed() {
        val items = buildDay(day, null, emptyList(), emptyList(), hideFreeBefore = 10 * 60)
        assertEquals(listOf("livre 600-1080"), describe(items))
    }

    @Test
    fun allDayBlockLeavesNoFreeTime() {
        val block = TimeBlock(id = 1, date = 0, allDay = true, reason = "Folga")
        val items = buildDay(day, null, emptyList(), listOf(block))
        assertEquals(listOf("bloqueio 0-1440"), describe(items))
    }

    @Test
    fun partialBlockSplitsTheDay() {
        val block = TimeBlock(id = 1, date = 0, startMin = 14 * 60, endMin = 16 * 60, reason = "Médico")
        val items = buildDay(day, null, emptyList(), listOf(block))
        assertEquals(listOf("livre 480-840", "bloqueio 840-960", "livre 960-1080"), describe(items))
    }

    @Test
    fun freeStartsSkipBusyRanges() {
        val busy = busyRanges(listOf(appt(1, 9 * 60)), emptyList(), (12 * 60) to (13 * 60))
        val starts = freeStarts(day, slotMin = 30, durationMin = 60, busy = busy)
        assertTrue(480 in starts)          // 08:00 cabe antes das 09:00
        assertFalse(510 in starts)         // 08:30 invadiria as 09:00
        assertFalse(540 in starts)         // 09:00 está ocupado
        assertTrue(600 in starts)          // 10:00
        assertTrue(660 in starts)          // 11:00 termina no início do almoço
        assertFalse(690 in starts)         // 11:30 invadiria o almoço
        assertTrue(780 in starts)          // 13:00
        assertEquals(1020, starts.last())  // 17:00 é o último: termina às 18:00
    }

    @Test
    fun freeStartsRespectNotBeforeAndClosedDays() {
        val starts = freeStarts(day, slotMin = 30, durationMin = 60, busy = emptyList(), notBefore = 15 * 60)
        assertEquals(900, starts.first())
        assertTrue(freeStarts(DayHours(open = false), 30, 60, emptyList()).isEmpty())
    }

    @Test
    fun conflictsNameTheOtherClient() {
        val existing = listOf(appt(1, 9 * 60, client = 7))
        val conflicts = findConflicts(9 * 60 + 30, 60, existing, emptyList(), null, mapOf(7L to "Maria"))
        assertEquals(1, conflicts.size)
        assertEquals("Maria", conflicts[0].what)

        // Encostado no fim do outro atendimento não é conflito.
        assertTrue(findConflicts(10 * 60, 60, existing, emptyList(), null, emptyMap()).isEmpty())
        // Ao editar, o próprio atendimento não conta.
        assertTrue(findConflicts(9 * 60, 60, existing, emptyList(), null, emptyMap(), excludeApptId = 1).isEmpty())
    }

    @Test
    fun conflictsIncludeBlocksAndLunch() {
        val block = TimeBlock(id = 1, date = 0, startMin = 14 * 60, endMin = 16 * 60, reason = "Médico")
        val conflicts = findConflicts(13 * 60 + 30, 60, emptyList(), listOf(block), (13 * 60) to (14 * 60), emptyMap())
        assertEquals(listOf("Bloqueio: Médico", "Almoço"), conflicts.map { it.what })
    }

    @Test
    fun repeatingSessions() {
        val first = LocalDate.of(2026, 11, 10)
        assertEquals(listOf(first), repeatDates(first, RepeatEvery.NONE, 5))
        assertEquals(
            listOf(first, first.plusDays(7), first.plusDays(14), first.plusDays(21), first.plusDays(28)),
            repeatDates(first, RepeatEvery.WEEK, 5)
        )
        assertEquals(first.plusDays(28), repeatDates(first, RepeatEvery.TWO_WEEKS, 3).last())
        assertEquals(LocalDate.of(2027, 1, 10), repeatDates(first, RepeatEvery.MONTH, 3).last())
    }

    @Test
    fun roundUp() {
        assertEquals(570, roundUpTo(557, 30))
        assertEquals(540, roundUpTo(540, 30))
    }
}
