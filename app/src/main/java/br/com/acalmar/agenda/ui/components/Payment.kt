package br.com.acalmar.agenda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.PayMethod
import br.com.acalmar.agenda.domain.RestChoice
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.restAfter
import br.com.acalmar.agenda.ui.theme.Brand

/** O que foi informado em um pagamento: valor, forma, dia do caixa e o que fazer com o restante. */
data class PaymentResult(
    val amountCents: Long,
    val method: PayMethod,
    val day: Long,
    val rest: RestChoice
)

/** "Hoje", "Ontem" ou a data por extenso curta. */
fun paymentDayLabel(day: Long, today: Long): String = when (day) {
    today -> "Hoje"
    today - 1 -> "Ontem"
    else -> formatDateShort(epochToDate(day))
}

/**
 * Campos de um pagamento: valor recebido, forma de pagamento e dia em que entra no caixa.
 * Quando o valor é menor que o devido, pergunta se o restante fica a receber ou foi desconto.
 */
@Composable
fun PaymentFields(
    dueCents: Long,
    amountCents: Long,
    onAmount: (Long) -> Unit,
    method: PayMethod,
    onMethod: (PayMethod) -> Unit,
    day: Long,
    today: Long,
    onPickDay: () -> Unit,
    rest: RestChoice,
    onRest: (RestChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MoneyField(cents = amountCents, onChange = onAmount, label = "Valor recebido", modifier = Modifier.fillMaxWidth())
        if (amountCents > 0) {
            PayMethodChips(selected = method, onSelect = onMethod)
            FieldRow(
                label = "Entra no caixa de",
                value = paymentDayLabel(day, today),
                icon = Icons.Outlined.CalendarMonth,
                onClick = onPickDay
            )
        }
        val left = restAfter(dueCents, amountCents)
        if (left > 0) {
            Text(
                "Restante: ${formatMoney(left)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Brand.Terracotta
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestChoice.entries.forEach { choice ->
                    ChoiceChip(choice.label, selected = rest == choice, onClick = { onRest(choice) })
                }
            }
            Text(
                if (rest == RestChoice.WAIVE) {
                    "O restante sai de \"A receber\" como desconto e não entra no caixa."
                } else {
                    "O restante continua em \"A receber\" no Caixa."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
        }
    }
}

/**
 * Receber um valor pendente: tudo, uma parte, ou dar baixa (desconto) no que não vai ser pago.
 * Para só dar baixa, deixe o valor recebido em zero e escolha "Foi desconto".
 */
@Composable
fun ReceiveDialog(
    title: String,
    dueCents: Long,
    defaultDay: Long,
    today: Long,
    onDismiss: () -> Unit,
    onConfirm: (PaymentResult) -> Unit
) {
    var amount by rememberSaveable { mutableLongStateOf(dueCents) }
    var method by rememberSaveable { mutableStateOf(PayMethod.PIX) }
    var day by rememberSaveable { mutableLongStateOf(defaultDay) }
    var rest by rememberSaveable { mutableStateOf(RestChoice.KEEP) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }

    val onlyWaive = amount == 0L && rest == RestChoice.WAIVE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Falta receber ${formatMoney(dueCents)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
                PaymentFields(
                    dueCents = dueCents,
                    amountCents = amount,
                    onAmount = { amount = it },
                    method = method,
                    onMethod = { method = it },
                    day = day,
                    today = today,
                    onPickDay = { pickingDay = true },
                    rest = rest,
                    onRest = { rest = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount > 0 || onlyWaive,
                onClick = { onConfirm(PaymentResult(amount, method, day, rest)) }
            ) { Text(if (onlyWaive) "Dar baixa" else "Receber", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (pickingDay) {
        DayPickerDialog(
            initialDay = day,
            onPick = {
                day = it
                pickingDay = false
            },
            onDismiss = { pickingDay = false }
        )
    }
}

/** Aviso mostrado depois de receber ou dar baixa. */
fun receiveMessage(dueCents: Long, result: PaymentResult, today: Long): String {
    val left = restAfter(dueCents, result.amountCents)
    val where = if (result.day == today) "no caixa de hoje" else "no caixa de ${formatDateShort(epochToDate(result.day))}"
    return when {
        result.amountCents > 0 && left > 0 && result.rest == RestChoice.WAIVE ->
            "Pagamento lançado $where · ${formatMoney(left)} de desconto"
        result.amountCents > 0 && left > 0 -> "Pagamento lançado $where · faltam ${formatMoney(left)}"
        result.amountCents > 0 -> "Pagamento lançado $where"
        else -> "Baixa de ${formatMoney(left)} registrada como desconto"
    }
}
