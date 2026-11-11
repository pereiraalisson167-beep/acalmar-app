package br.com.acalmar.agenda.domain

import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.data.Service

// "Hora de chamar de volta": quem já passou (ou está perto) do tempo de retorno do último
// atendimento e ainda não tem novo horário marcado. Sem depender do Android.

/** Cliente que está na hora de chamar de volta. */
data class RecallItem(
    val client: Client,
    val serviceName: String,
    /** Dia do último atendimento concluído. */
    val lastDate: Long,
    /** Dia previsto para o retorno. */
    val dueDate: Long
)

/** Quantos dias antes do retorno a cliente já aparece na lista. */
const val RECALL_HORIZON_DAYS = 7

/** Tempo de retorno usado quando o serviço do último atendimento não existe mais. */
const val DEFAULT_RETURN_DAYS = 30

/**
 * Regras:
 * - vale o último atendimento concluído da cliente e o tempo de retorno do serviço dele;
 * - quem já tem horário marcado de hoje em diante não entra;
 * - serviço com tempo de retorno 0 não gera aviso;
 * - "lembrar depois" esconde até a data escolhida; "não chamar" esconde até o próximo atendimento.
 */
fun computeRecalls(
    clients: List<Client>,
    appts: List<Appointment>,
    services: List<Service>,
    today: Long,
    horizonDays: Int = RECALL_HORIZON_DAYS
): List<RecallItem> {
    val returnByService = services.associate { it.id to it.returnDays }
    val byClient = appts.groupBy { it.clientId }
    val out = mutableListOf<RecallItem>()
    for (client in clients) {
        val list = byClient[client.id] ?: continue
        if (list.any { it.status == ApptStatus.AGENDADO && it.date >= today }) continue
        val last = list
            .filter { it.status == ApptStatus.CONCLUIDO }
            .maxWithOrNull(compareBy<Appointment>({ it.date }, { it.startMin }))
            ?: continue
        val days = last.serviceId?.let { returnByService[it] } ?: DEFAULT_RETURN_DAYS
        if (days <= 0) continue
        val due = last.date + days
        if (due > today + horizonDays) continue
        if (client.recallDismissedFor == last.date) continue
        if ((client.recallSnoozeUntil ?: Long.MIN_VALUE) > today) continue
        out += RecallItem(client, last.serviceName, last.date, due)
    }
    return out.sortedWith(compareBy<RecallItem>({ it.dueDate }, { sortKey(it.client.name) }))
}

/** "atrasado 3 dias", "é hoje" ou "em 2 dias". */
fun recallDueLabel(dueDate: Long, today: Long): String {
    val diff = dueDate - today
    return when {
        diff == 0L -> "é hoje"
        diff == 1L -> "é amanhã"
        diff > 1L -> "em $diff dias"
        diff == -1L -> "atrasado 1 dia"
        else -> "atrasado ${-diff} dias"
    }
}

/** "hoje", "ontem" ou "há 32 dias". */
fun daysAgoLabel(date: Long, today: Long): String {
    val diff = today - date
    return when {
        diff <= 0L -> "hoje"
        diff == 1L -> "ontem"
        else -> "há $diff dias"
    }
}

/** Mensagem pronta para enviar pelo WhatsApp. */
fun recallMessage(item: RecallItem, today: Long): String {
    val name = firstName(item.client.name)
    val since = today - item.lastDate
    val time = when {
        since <= 1L -> "Faz pouco tempo"
        since < 14L -> "Já faz $since dias"
        since < 60L -> "Já faz ${since / 7} semanas"
        else -> "Já faz ${since / 30} meses"
    }
    return "Olá, $name! Tudo bem? $time do seu último atendimento (${item.serviceName}). " +
        "Vamos agendar o próximo?"
}
