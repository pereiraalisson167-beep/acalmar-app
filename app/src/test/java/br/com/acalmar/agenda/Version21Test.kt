package br.com.acalmar.agenda

import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.AutoBackup
import br.com.acalmar.agenda.data.BackupFile
import br.com.acalmar.agenda.data.CashEntry
import br.com.acalmar.agenda.data.ClientPackage
import br.com.acalmar.agenda.data.TimeBlock
import br.com.acalmar.agenda.domain.RepeatEvery
import br.com.acalmar.agenda.domain.RestChoice
import br.com.acalmar.agenda.domain.blockRun
import br.com.acalmar.agenda.domain.computePackageStates
import br.com.acalmar.agenda.domain.computeReceivables
import br.com.acalmar.agenda.domain.defaultPaymentDay
import br.com.acalmar.agenda.domain.discountFor
import br.com.acalmar.agenda.domain.repeatCountFor
import br.com.acalmar.agenda.domain.repeatDates
import br.com.acalmar.agenda.domain.repeatLimit
import br.com.acalmar.agenda.domain.restAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Regras corrigidas ou criadas na versão 2.1. */
class Version21Test {

    private fun appt(id: Long, price: Long, discount: Long = 0, pkgId: Long? = null) = Appointment(
        id = id,
        clientId = 1,
        serviceName = "Limpeza de pele",
        date = 100 + id,
        startMin = 9 * 60,
        durationMin = 60,
        priceCents = price,
        status = ApptStatus.CONCLUIDO,
        clientPackageId = pkgId,
        discountCents = discount
    )

    private fun pkg(paidBefore: Long = 0, discount: Long = 0) = ClientPackage(
        id = 10,
        clientId = 1,
        serviceName = "Drenagem linfática",
        totalSessions = 5,
        priceCents = 32000,
        purchasedOn = 100,
        paidBeforeCents = paidBefore,
        discountCents = discount
    )

    // ---------- Desconto não vira dívida

    @Test
    fun desconto_noAtendimentoSaiDoAReceber() {
        // Serviço de R$ 100, recebeu R$ 80 e deu R$ 20 de desconto: não sobra nada a receber.
        val a = appt(1, price = 10000, discount = 2000)
        val cash = listOf(CashEntry(id = 1, date = 101, amountCents = 8000, appointmentId = 1))
        assertTrue(computeReceivables(listOf(a), emptyList(), cash).isEmpty())
    }

    @Test
    fun desconto_semBaixaORestanteContinuaAReceber() {
        val a = appt(1, price = 10000)
        val cash = listOf(CashEntry(id = 1, date = 101, amountCents = 8000, appointmentId = 1))
        assertEquals(2000L, computeReceivables(listOf(a), emptyList(), cash).single().dueCents)
    }

    @Test
    fun desconto_contasDoRestante() {
        assertEquals(2000L, restAfter(10000, 8000))
        assertEquals(0L, restAfter(10000, 12000))
        assertEquals(2000L, discountFor(10000, 8000, RestChoice.WAIVE))
        assertEquals(0L, discountFor(10000, 8000, RestChoice.KEEP))
        assertEquals(10000L, discountFor(10000, 0, RestChoice.WAIVE))
    }

    // ---------- Pacote comprado antes do app

    @Test
    fun pacoteAntigo_jaPagoNaoEntraNoCaixaNemViraDivida() {
        val states = computePackageStates(listOf(pkg(paidBefore = 32000)), emptyList(), emptyList())
        assertEquals(0L, states.single().dueCents)
        assertEquals(0L, states.single().paidCents) // nada passou pelo caixa
        assertTrue(computeReceivables(emptyList(), states, emptyList()).isEmpty())
    }

    @Test
    fun pacoteAntigo_pagoEmParteDeixaSoORestante() {
        val cash = listOf(CashEntry(id = 1, date = 101, amountCents = 5000, clientPackageId = 10))
        val states = computePackageStates(listOf(pkg(paidBefore = 20000, discount = 2000)), emptyList(), cash)
        // 320 - 200 (antes do app) - 20 (desconto) - 50 (caixa) = 50
        assertEquals(5000L, states.single().dueCents)
        assertEquals(5000L, computeReceivables(emptyList(), states, cash).single().dueCents)
    }

    // ---------- Sessões que se repetem com pacote

    @Test
    fun repeticao_pacoteComUmaSessaoNaoRepete() {
        val limit = repeatLimit(usesPackage = true, packageToSchedule = 1)
        assertEquals(1, limit)
        assertEquals(1, repeatCountFor(5, limit))
        // Com o limite em 1, a tela agenda uma única data.
        assertEquals(1, repeatDates(LocalDate.of(2026, 11, 16), RepeatEvery.NONE, repeatCountFor(5, limit)).size)
    }

    @Test
    fun repeticao_respeitaOQueRestaNoPacote() {
        assertEquals(3, repeatCountFor(5, repeatLimit(true, 3)))
        assertEquals(2, repeatCountFor(1, repeatLimit(true, 3)))
        assertEquals(30, repeatCountFor(99, repeatLimit(false, 0)))
        assertEquals(1, repeatCountFor(5, repeatLimit(true, 0)))
    }

    // ---------- Bloqueio de vários dias

    private fun block(id: Long, date: Long, reason: String = "Férias", allDay: Boolean = true, start: Int = 0, end: Int = 1440) =
        TimeBlock(id = id, date = date, startMin = start, endMin = end, allDay = allDay, reason = reason)

    @Test
    fun bloqueio_juntaOsDiasSeguidos() {
        val all = listOf(block(1, 10), block(2, 11), block(3, 12), block(4, 14), block(5, 13, reason = "Feriado"))
        assertEquals(listOf(1L, 2L, 3L), blockRun(all[1], all).map { it.id })
        // O dia 14 fica de fora: o dia 13 tem outro motivo e quebra a sequência.
        assertEquals(listOf(4L), blockRun(all[3], all).map { it.id })
    }

    @Test
    fun bloqueio_periodosDiferentesNaoSeJuntam() {
        val lunch = block(1, 10, reason = "Almoço", allDay = false, start = 720, end = 780)
        val other = block(2, 11, reason = "Almoço", allDay = false, start = 720, end = 840)
        assertEquals(listOf(1L), blockRun(lunch, listOf(lunch, other)).map { it.id })
    }

    // ---------- Dia do pagamento

    @Test
    fun pagamento_entraNoDiaDoAtendimentoQuandoJaPassou() {
        assertEquals(95L, defaultPaymentDay(eventDay = 95, today = 100))
        assertEquals(100L, defaultPaymentDay(eventDay = 100, today = 100))
        assertEquals(100L, defaultPaymentDay(eventDay = 105, today = 100)) // adiantado: vale hoje
    }

    // ---------- Backup

    @Test
    fun backupAutomatico_rodizioDeTresArquivos() {
        val names = (0L..5L).map { AutoBackup.fileNameFor(20000 + it) }
        assertEquals(3, names.toSet().size)
        assertEquals(names[0], names[3])
        assertTrue(names.all { it.startsWith("acalmar-backup-automatico-") && it.endsWith(".zip") })
    }

    @Test
    fun backup_daVersao2ContinuaValido() {
        // Arquivo gravado pela versão 2.0.0: sem os campos de desconto e de "pago antes do app".
        val v2 = """
            {"app":"acalmar","schema":2,"exportedAt":1,
             "clients":[{"id":1,"name":"Ana"}],
             "clientPackages":[{"id":10,"clientId":1,"serviceName":"Drenagem","totalSessions":5,"priceCents":32000,"purchasedOn":100}],
             "appointments":[{"id":1,"clientId":1,"serviceName":"Limpeza","date":101,"startMin":540,"durationMin":60,"priceCents":9500,"status":"CONCLUIDO","evolution":"ok"}]}
        """.trimIndent()
        val file = BackupFile.parse(v2)
        assertEquals(0L, file.appointments.single().discountCents)
        assertEquals(0L, file.clientPackages.single().paidBeforeCents)
        assertEquals(0L, file.clientPackages.single().discountCents)
        assertEquals("ok", file.appointments.single().evolution)
    }
}
