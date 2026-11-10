package br.com.acalmar.agenda.ui.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.CashEntry
import br.com.acalmar.agenda.data.CashKind
import br.com.acalmar.agenda.data.PayMethod
import br.com.acalmar.agenda.domain.Receivable
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatDayMonthLong
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.monthName
import br.com.acalmar.agenda.domain.relativeLabel
import br.com.acalmar.agenda.domain.summarize
import br.com.acalmar.agenda.domain.weekdayName
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.agenda.PaymentDialog
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.DayPickerDialog
import br.com.acalmar.agenda.ui.components.EmptyState
import br.com.acalmar.agenda.ui.components.MoneyField
import br.com.acalmar.agenda.ui.components.PayMethodChips
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Caixa do dia: entradas, saídas, saldo e o que ainda falta receber. */
@Composable
fun CashScreen(onOpenClient: (Long) -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()

    var selectedDay by rememberSaveable { mutableLongStateOf(todayDay) }
    var pickingDate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CashEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf<Receivable?>(null) }

    val allCash by repo.cash.collectAsStateWithLifecycle(initialValue = emptyList())
    val receivables by repo.receivables.collectAsStateWithLifecycle(initialValue = emptyList())
    val clients by repo.clients.collectAsStateWithLifecycle(initialValue = emptyList())

    val date = epochToDate(selectedDay)
    val names = remember(clients) { clients.associate { it.id to it.name } }
    val entries = remember(allCash, selectedDay) {
        allCash.filter { it.date == selectedDay }.sortedWith(compareBy<CashEntry>({ it.createdAt }, { it.id }))
    }
    val summary = remember(entries) { summarize(entries) }
    val monthSummary = remember(allCash, date.year, date.monthValue) {
        val first = date.withDayOfMonth(1).toEpochDay()
        val last = date.withDayOfMonth(date.lengthOfMonth()).toEpochDay()
        summarize(allCash.filter { it.date in first..last })
    }
    val totalDue = receivables.sumOf { it.dueCents }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Caixa", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            (relativeLabel(date, today)?.let { "$it · " } ?: "") + "${weekdayName(date)}, ${formatDayMonthLong(date)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.BrownSoft
                        )
                    }
                    if (selectedDay != todayDay) {
                        TextButton(onClick = { selectedDay = todayDay }) { Text("Hoje") }
                    }
                    IconButton(onClick = { selectedDay -= 1 }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Dia anterior", tint = Brand.Bronze)
                    }
                    IconButton(onClick = { pickingDate = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data", tint = Brand.Bronze)
                    }
                    IconButton(onClick = { selectedDay += 1 }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Próximo dia", tint = Brand.Bronze)
                    }
                }
            }

            // ---- Resumo do dia
            item(key = "summary") {
                SoftCard(Modifier.fillMaxWidth(), container = Brand.Beige, border = Brand.Beige) {
                    Column(Modifier.padding(18.dp)) {
                        Text("SALDO DO DIA", style = MaterialTheme.typography.labelMedium, color = Brand.BronzeDark)
                        Text(
                            formatMoney(summary.balanceCents),
                            style = MaterialTheme.typography.displaySmall,
                            color = Brand.Brown
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryPill("Entradas", formatMoney(summary.inCents), Brand.Sage, Modifier.weight(1f))
                            SummaryPill("Saídas", formatMoney(summary.outCents), Brand.Terracotta, Modifier.weight(1f))
                        }
                        if (summary.inByMethod.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                summary.inByMethod.forEach { (m, cents) ->
                                    Tag("${m.label} ${formatMoney(cents)}", Brand.Card, Brand.BronzeDark)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "month") {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(
                        "Em ${monthName(date)}: entradas ${formatMoney(monthSummary.inCents)} · saídas ${formatMoney(monthSummary.outCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )
                }
            }

            // ---- Lançamentos do dia
            item(key = "entries-title") { SectionLabel("Lançamentos", Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (entries.isEmpty()) {
                item(key = "entries-empty") {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "Nada lançado neste dia",
                        subtitle = "Ao concluir um atendimento, o pagamento entra aqui sozinho."
                    )
                }
            }
            items(entries, key = { "e${it.id}" }) { entry ->
                EntryRow(entry, onClick = { editing = entry })
            }

            // ---- A receber
            if (receivables.isNotEmpty()) {
                item(key = "due-title") {
                    Row(Modifier.padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("A receber", Modifier.weight(1f))
                        Text(formatMoney(totalDue), style = MaterialTheme.typography.labelLarge, color = Brand.Terracotta)
                    }
                }
                items(receivables, key = { "r${it.appointmentId}-${it.clientPackageId}" }) { r ->
                    SoftCard(Modifier.fillMaxWidth(), onClick = { onOpenClient(r.clientId) }) {
                        Row(
                            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    names[r.clientId] ?: "Cliente",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${r.title} · ${formatDateShort(epochToDate(r.date))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.BrownSoft,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(formatMoney(r.dueCents), color = Brand.Terracotta, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = { receiving = r }) { Text("Receber") }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            containerColor = Brand.Bronze,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Add, contentDescription = "Lançar") },
            text = { Text("Lançar") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (pickingDate) {
        DayPickerDialog(
            initialDay = selectedDay,
            onPick = {
                selectedDay = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false }
        )
    }

    if (creating || editing != null) {
        val entry = editing
        EntryDialog(
            entry = entry,
            day = selectedDay,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { saved ->
                creating = false
                editing = null
                scope.launch {
                    repo.saveCash(saved)
                    notifier.show(if (entry == null) "Lançamento salvo" else "Lançamento atualizado")
                }
            },
            onDelete = {
                creating = false
                editing = null
                if (entry != null) {
                    scope.launch {
                        repo.deleteCash(entry.id)
                        notifier.show("Lançamento excluído")
                    }
                }
            }
        )
    }

    receiving?.let { r ->
        PaymentDialog(
            title = "Receber de ${names[r.clientId] ?: "cliente"}",
            suggestedCents = r.dueCents,
            allowLater = false,
            confirmLabel = "Receber",
            onDismiss = { receiving = null },
            onConfirm = { amount, method ->
                receiving = null
                scope.launch {
                    repo.receive(r, amount, method, todayDay)
                    if (amount > 0) notifier.show("Pagamento lançado no caixa de hoje")
                }
            }
        )
    }
}

@Composable
private fun SummaryPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = Brand.Card) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.BrownSoft)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EntryRow(entry: CashEntry, onClick: () -> Unit) {
    val isIn = entry.kind == CashKind.ENTRADA
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (isIn) Brand.SageSoft else Brand.TerracottaSoft, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isIn) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        contentDescription = if (isIn) "Entrada" else "Saída",
                        tint = if (isIn) Brand.Sage else Brand.Terracotta,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.description.ifBlank { if (isIn) "Entrada" else "Saída" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(entry.method.label, style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
            }
            Text(
                (if (isIn) "+ " else "− ") + formatMoney(entry.amountCents),
                style = MaterialTheme.typography.titleMedium,
                color = if (isIn) Brand.Sage else Brand.Terracotta,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Cria ou edita um lançamento avulso (entrada ou saída). */
@Composable
private fun EntryDialog(
    entry: CashEntry?,
    day: Long,
    onDismiss: () -> Unit,
    onSave: (CashEntry) -> Unit,
    onDelete: () -> Unit
) {
    var kind by remember { mutableStateOf(entry?.kind ?: CashKind.ENTRADA) }
    var amount by remember { mutableLongStateOf(entry?.amountCents ?: 0L) }
    var method by remember { mutableStateOf(entry?.method ?: PayMethod.PIX) }
    var description by remember { mutableStateOf(entry?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Novo lançamento" else "Editar lançamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Entrada", selected = kind == CashKind.ENTRADA, onClick = { kind = CashKind.ENTRADA })
                    ChoiceChip("Saída", selected = kind == CashKind.SAIDA, onClick = { kind = CashKind.SAIDA })
                }
                MoneyField(cents = amount, onChange = { amount = it }, label = "Valor", modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(if (kind == CashKind.ENTRADA) "Descrição" else "Com o que foi gasto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                PayMethodChips(selected = method, onSelect = { method = it })
                if (entry != null) {
                    TextButton(onClick = onDelete) { Text("Excluir lançamento", color = Brand.Terracotta) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount > 0,
                onClick = {
                    val base = entry ?: CashEntry(date = day, amountCents = 0)
                    onSave(base.copy(kind = kind, amountCents = amount, method = method, description = description.trim()))
                }
            ) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
