package br.com.acalmar.agenda.domain

import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.CashEntry
import br.com.acalmar.agenda.data.CashKind
import br.com.acalmar.agenda.data.ClientPackage
import br.com.acalmar.agenda.data.PayMethod

// Regras de pacotes, valores a receber e resumo do caixa (sem depender do Android).

/** Situação de um pacote comprado: sessões usadas, agendadas e quanto já foi pago. */
data class PackageState(
    val pkg: ClientPackage,
    val used: Int,
    val scheduled: Int,
    val paidCents: Long
) {
    /** Sessões que ainda não foram feitas. */
    val remaining: Int get() = (pkg.totalSessions - used).coerceAtLeast(0)

    /** Sessões que ainda podem ser agendadas. */
    val toSchedule: Int get() = (pkg.totalSessions - used - scheduled).coerceAtLeast(0)

    val dueCents: Long get() = (pkg.priceCents - paidCents).coerceAtLeast(0)

    fun isExpired(today: Long): Boolean = (pkg.expiresOn ?: Long.MAX_VALUE) < today

    fun isActive(today: Long): Boolean = remaining > 0 && !isExpired(today)
}

/**
 * Uma sessão conta como usada quando foi concluída, ou quando a cliente faltou sem avisar
 * (falta marcada com desconto). Desmarcar com antecedência não desconta.
 */
fun computePackageStates(
    packages: List<ClientPackage>,
    appts: List<Appointment>,
    cash: List<CashEntry>
): List<PackageState> {
    val apptsByPkg = appts.filter { it.clientPackageId != null }.groupBy { it.clientPackageId }
    val paidByPkg = cash
        .filter { it.clientPackageId != null && it.kind == CashKind.ENTRADA }
        .groupBy { it.clientPackageId }
    return packages.map { p ->
        val list = apptsByPkg[p.id].orEmpty()
        val used = p.usedBefore + list.count {
            it.status == ApptStatus.CONCLUIDO || (it.status == ApptStatus.FALTOU && it.chargesPackage)
        }
        val scheduled = list.count { it.status == ApptStatus.AGENDADO }
        PackageState(p, used, scheduled, paidByPkg[p.id].orEmpty().sumOf { it.amountCents })
    }
}

/** Um valor que a cliente ainda deve: atendimento concluído sem pagamento ou pacote não quitado. */
data class Receivable(
    val clientId: Long,
    val title: String,
    val dueCents: Long,
    val date: Long,
    val appointmentId: Long? = null,
    val clientPackageId: Long? = null
)

fun computeReceivables(
    appts: List<Appointment>,
    states: List<PackageState>,
    cash: List<CashEntry>
): List<Receivable> {
    val paidByAppt = cash
        .filter { it.appointmentId != null && it.kind == CashKind.ENTRADA }
        .groupBy { it.appointmentId }
        .mapValues { entry -> entry.value.sumOf { it.amountCents } }
    val out = mutableListOf<Receivable>()
    appts.forEach { a ->
        if (a.status == ApptStatus.CONCLUIDO && a.clientPackageId == null && a.priceCents > 0) {
            val due = a.priceCents - (paidByAppt[a.id] ?: 0L)
            if (due > 0) out += Receivable(a.clientId, a.serviceName, due, a.date, appointmentId = a.id)
        }
    }
    states.forEach { s ->
        if (s.dueCents > 0) {
            out += Receivable(
                clientId = s.pkg.clientId,
                title = "Pacote · ${s.pkg.serviceName} (${s.pkg.totalSessions} sessões)",
                dueCents = s.dueCents,
                date = s.pkg.purchasedOn,
                clientPackageId = s.pkg.id
            )
        }
    }
    return out.sortedBy { it.date }
}

/** Totais do caixa de um dia. */
data class CashSummary(
    val inCents: Long,
    val outCents: Long,
    val inByMethod: Map<PayMethod, Long>
) {
    val balanceCents: Long get() = inCents - outCents
}

fun summarize(entries: List<CashEntry>): CashSummary {
    val ins = entries.filter { it.kind == CashKind.ENTRADA }
    val outs = entries.filter { it.kind == CashKind.SAIDA }
    val byMethod = PayMethod.entries
        .associateWith { m -> ins.filter { it.method == m }.sumOf { it.amountCents } }
        .filterValues { it > 0 }
    return CashSummary(ins.sumOf { it.amountCents }, outs.sumOf { it.amountCents }, byMethod)
}
