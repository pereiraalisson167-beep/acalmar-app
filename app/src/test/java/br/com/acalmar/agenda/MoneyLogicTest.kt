package br.com.acalmar.agenda

import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.BackupFile
import br.com.acalmar.agenda.data.CashEntry
import br.com.acalmar.agenda.data.CashKind
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.data.ClientPackage
import br.com.acalmar.agenda.data.PayMethod
import br.com.acalmar.agenda.data.Service
import br.com.acalmar.agenda.data.Setting
import br.com.acalmar.agenda.data.WorkHours
import br.com.acalmar.agenda.domain.computePackageStates
import br.com.acalmar.agenda.domain.computeReceivables
import br.com.acalmar.agenda.domain.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyLogicTest {

    private val pkg = ClientPackage(
        id = 10,
        clientId = 1,
        serviceName = "Drenagem linfática",
        totalSessions = 5,
        priceCents = 32000,
        purchasedOn = 100
    )

    private fun appt(
        id: Long,
        status: ApptStatus,
        pkgId: Long? = 10,
        charges: Boolean = true,
        price: Long = 0
    ) = Appointment(
        id = id,
        clientId = 1,
        serviceName = "Drenagem linfática",
        date = 100 + id,
        startMin = 9 * 60,
        durationMin = 60,
        priceCents = price,
        status = status,
        clientPackageId = pkgId,
        chargesPackage = charges
    )

    @Test
    fun packageUsageFollowsTheNoShowRule() {
        val appts = listOf(
            appt(1, ApptStatus.CONCLUIDO),
            appt(2, ApptStatus.FALTOU, charges = true),   // faltou sem avisar: desconta
            appt(3, ApptStatus.FALTOU, charges = false),  // falta sem desconto
            appt(4, ApptStatus.DESMARCADO),               // avisou com antecedência: não desconta
            appt(5, ApptStatus.AGENDADO)
        )
        val cash = listOf(CashEntry(id = 1, date = 100, amountCents = 20000, clientPackageId = 10))

        val state = computePackageStates(listOf(pkg), appts, cash).single()

        assertEquals(2, state.used)
        assertEquals(1, state.scheduled)
        assertEquals(3, state.remaining)
        assertEquals(2, state.toSchedule)
        assertEquals(20000L, state.paidCents)
        assertEquals(12000L, state.dueCents)
        assertTrue(state.isActive(today = 200))
    }

    @Test
    fun sessionsDoneBeforeTheAppCount() {
        val state = computePackageStates(listOf(pkg.copy(usedBefore = 2)), emptyList(), emptyList()).single()
        assertEquals(2, state.used)
        assertEquals(3, state.remaining)
    }

    @Test
    fun expiredOrFinishedPackageIsNotActive() {
        val expiring = computePackageStates(listOf(pkg.copy(expiresOn = 150)), emptyList(), emptyList()).single()
        assertTrue(expiring.isActive(today = 150))
        assertFalse(expiring.isActive(today = 151))

        val finished = computePackageStates(listOf(pkg.copy(usedBefore = 5)), emptyList(), emptyList()).single()
        assertFalse(finished.isActive(today = 100))
    }

    @Test
    fun receivablesListOnlyWhatIsStillOwed() {
        val appts = listOf(
            appt(1, ApptStatus.CONCLUIDO, pkgId = null, price = 7500),  // pago em parte
            appt(2, ApptStatus.CONCLUIDO, pkgId = null, price = 7500),  // pago
            appt(3, ApptStatus.AGENDADO, pkgId = null, price = 7500),   // ainda não atendida
            appt(4, ApptStatus.CONCLUIDO)                               // sessão de pacote
        )
        val cash = listOf(
            CashEntry(id = 1, date = 101, amountCents = 5000, appointmentId = 1),
            CashEntry(id = 2, date = 102, amountCents = 7500, appointmentId = 2),
            CashEntry(id = 3, date = 100, amountCents = 32000, clientPackageId = 10)
        )
        val states = computePackageStates(listOf(pkg), appts, cash)

        val receivables = computeReceivables(appts, states, cash)

        assertEquals(1, receivables.size)
        assertEquals(2500L, receivables[0].dueCents)
        assertEquals(1L, receivables[0].appointmentId)
    }

    @Test
    fun unpaidPackageIsReceivable() {
        val states = computePackageStates(listOf(pkg), emptyList(), emptyList())
        val receivables = computeReceivables(emptyList(), states, emptyList())
        assertEquals(1, receivables.size)
        assertEquals(32000L, receivables[0].dueCents)
        assertEquals(10L, receivables[0].clientPackageId)
    }

    @Test
    fun cashSummaryByMethod() {
        val summary = summarize(
            listOf(
                CashEntry(id = 1, date = 1, amountCents = 7000, method = PayMethod.PIX),
                CashEntry(id = 2, date = 1, amountCents = 7500, method = PayMethod.DINHEIRO),
                CashEntry(id = 3, date = 1, amountCents = 8000, method = PayMethod.PIX),
                CashEntry(id = 4, date = 1, kind = CashKind.SAIDA, amountCents = 3000, method = PayMethod.DINHEIRO)
            )
        )
        assertEquals(22500L, summary.inCents)
        assertEquals(3000L, summary.outCents)
        assertEquals(19500L, summary.balanceCents)
        assertEquals(mapOf(PayMethod.PIX to 15000L, PayMethod.DINHEIRO to 7500L), summary.inByMethod)
    }

    @Test
    fun backupRoundTrip() {
        val file = BackupFile(
            app = BackupFile.APP_TAG,
            exportedAt = 1,
            clients = listOf(Client(id = 1, name = "Maria Conceição", phone = "11999990000", birthday = "14/03")),
            services = listOf(Service(id = 1, name = "Drenagem linfática", priceCents = 7000)),
            clientPackages = listOf(pkg),
            appointments = listOf(appt(1, ApptStatus.CONCLUIDO).copy(serviceId = 1, seriesId = "abc")),
            cash = listOf(CashEntry(id = 1, date = 100, amountCents = 7000, method = PayMethod.DINHEIRO, appointmentId = 1)),
            settings = listOf(Setting("work_hours", WorkHours().toJson()))
        )
        assertEquals(file, BackupFile.parse(file.toJson()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsOtherJsonFiles() {
        BackupFile.parse("{\"hello\": 1}")
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsFilesThatAreNotJson() {
        BackupFile.parse("isto não é um backup")
    }

    @Test
    fun workHoursDefaultsAndRoundTrip() {
        val hours = WorkHours.parse(null)
        assertTrue(hours.forDate(LocalDate.of(2026, 11, 10)).open)   // terça-feira
        assertFalse(hours.forDate(LocalDate.of(2026, 11, 8)).open)   // domingo
        assertEquals(hours, WorkHours.parse(hours.toJson()))
        assertEquals(WorkHours(), WorkHours.parse("texto inválido"))
    }
}
