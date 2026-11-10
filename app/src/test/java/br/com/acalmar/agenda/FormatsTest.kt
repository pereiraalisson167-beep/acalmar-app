package br.com.acalmar.agenda

import br.com.acalmar.agenda.domain.formatDateLong
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatDuration
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.formatMoneyPlain
import br.com.acalmar.agenda.domain.formatPhone
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.friendlyDate
import br.com.acalmar.agenda.domain.initials
import br.com.acalmar.agenda.domain.isValidBirthday
import br.com.acalmar.agenda.domain.maskBirthday
import br.com.acalmar.agenda.domain.sortKey
import br.com.acalmar.agenda.domain.weekStart
import br.com.acalmar.agenda.domain.weekdayName
import br.com.acalmar.agenda.domain.whatsappNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FormatsTest {

    @Test
    fun money() {
        assertEquals("R$ 75,00", formatMoney(7500))
        assertEquals("R$ 1.234,56", formatMoney(123456))
        assertEquals("R$ 0,05", formatMoney(5))
        assertEquals("-10,00", formatMoneyPlain(-1000))
        assertEquals("1.000.000,00", formatMoneyPlain(100000000))
    }

    @Test
    fun timeAndDuration() {
        assertEquals("09:30", formatTime(570))
        assertEquals("00:00", formatTime(0))
        assertEquals("24:00", formatTime(1440))
        assertEquals("45 min", formatDuration(45))
        assertEquals("1h", formatDuration(60))
        assertEquals("1h30", formatDuration(90))
    }

    @Test
    fun dates() {
        val tuesday = LocalDate.of(2026, 11, 10)
        assertEquals("Terça-feira", weekdayName(tuesday))
        assertEquals("10 de novembro de 2026", formatDateLong(tuesday))
        assertEquals("10/11/2026", formatDateShort(tuesday))
        assertEquals("Hoje", friendlyDate(tuesday, tuesday))
        assertEquals("Amanhã", friendlyDate(tuesday.plusDays(1), tuesday))
        assertEquals("Sáb, 14 nov", friendlyDate(LocalDate.of(2026, 11, 14), tuesday))
        assertEquals("Sáb, 2 jan 2027", friendlyDate(LocalDate.of(2027, 1, 2), tuesday))
        // A semana começa no domingo.
        assertEquals(LocalDate.of(2026, 11, 8), weekStart(tuesday))
        assertEquals(LocalDate.of(2026, 11, 8), weekStart(LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 11, 8), weekStart(LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun phone() {
        assertEquals("(11) 99848-4660", formatPhone("11998484660"))
        assertEquals("(11) 99848-4660", formatPhone("+55 11 99848-4660"))
        assertEquals("(11) 3333-4444", formatPhone("1133334444"))
        assertEquals("5511998484660", whatsappNumber("(11) 99848-4660"))
        assertEquals("5511998484660", whatsappNumber("+55 11 99848-4660"))
        assertEquals("5511998484660", whatsappNumber("011 99848-4660"))
        assertNull(whatsappNumber("99848-4660"))
        assertNull(whatsappNumber(""))
    }

    @Test
    fun names() {
        assertEquals("MS", initials("maria aparecida silva"))
        assertEquals("K", initials("Karina"))
        assertEquals("?", initials("  "))
        assertEquals("conceicao", sortKey("Conceição"))
        assertTrue(sortKey("Ângela") < sortKey("Bruna"))
    }

    @Test
    fun birthday() {
        assertEquals("14/03", maskBirthday("1403"))
        assertEquals("14/03/1990", maskBirthday("14031990"))
        assertEquals("14/0", maskBirthday("140"))
        assertTrue(isValidBirthday(""))
        assertTrue(isValidBirthday("14/03"))
        assertTrue(isValidBirthday("29/02/1992"))
        assertFalse(isValidBirthday("31/04"))
        assertFalse(isValidBirthday("14/13"))
        assertFalse(isValidBirthday("14/0"))
    }
}
