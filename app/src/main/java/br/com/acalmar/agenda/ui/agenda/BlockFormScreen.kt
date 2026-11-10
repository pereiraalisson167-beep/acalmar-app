package br.com.acalmar.agenda.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.TimeBlock
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateLong
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.weekdayName
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.DayPickerDialog
import br.com.acalmar.agenda.ui.components.FieldRow
import br.com.acalmar.agenda.ui.components.MinutePickerDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

private enum class BlockDialog { NONE, DATE, UNTIL, START, END, DELETE }

private val REASONS = listOf("Almoço", "Folga", "Compromisso pessoal", "Feriado", "Férias")
private const val MAX_BLOCK_DAYS = 90L

/** Bloqueio de horário: um período do dia, o dia inteiro ou vários dias seguidos. */
@Composable
fun BlockFormScreen(blockId: Long, initialDate: Long, initialStart: Int, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val editing = blockId != 0L

    var day by remember { mutableLongStateOf(if (initialDate >= 0) initialDate else todayEpochDay()) }
    var untilDay by remember { mutableStateOf<Long?>(null) }
    var allDay by remember { mutableStateOf(false) }
    var startMin by remember { mutableIntStateOf(if (initialStart >= 0) initialStart else 12 * 60) }
    var endMin by remember { mutableIntStateOf(if (initialStart >= 0) (initialStart + 60).coerceAtMost(24 * 60 - 1) else 13 * 60) }
    var reason by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(BlockDialog.NONE) }

    LaunchedEffect(blockId) {
        if (editing) {
            repo.getBlock(blockId)?.let { b ->
                day = b.date
                allDay = b.allDay
                if (!b.allDay) {
                    startMin = b.startMin
                    endMin = b.endMin
                }
                reason = b.reason
            }
        }
    }

    val date = epochToDate(day)
    val validTime = allDay || endMin > startMin
    val until = untilDay?.takeIf { it > day }
    val days = if (until != null) (until - day + 1).coerceAtMost(MAX_BLOCK_DAYS) else 1L

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = if (editing) "Editar bloqueio" else "Bloquear horário",
                onBack = onDone,
                actions = {
                    if (editing) {
                        IconButton(onClick = { dialog = BlockDialog.DELETE }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Excluir bloqueio", tint = Brand.Terracotta)
                        }
                    }
                }
            )
        },
        bottomBar = {
            PrimaryButton(
                text = if (editing) "Salvar alterações" else if (days > 1) "Bloquear $days dias" else "Bloquear",
                enabled = validTime,
                onClick = {
                    scope.launch {
                        for (i in 0 until days) {
                            repo.saveBlock(
                                TimeBlock(
                                    id = if (editing) blockId else 0,
                                    date = day + i,
                                    startMin = if (allDay) 0 else startMin,
                                    endMin = if (allDay) 24 * 60 else endMin,
                                    allDay = allDay,
                                    reason = reason.trim()
                                )
                            )
                        }
                        notifier.show(if (editing) "Bloqueio atualizado" else "Horário bloqueado")
                        onDone()
                    }
                },
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Motivo (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONS.forEach { r -> ChoiceChip(r, selected = reason == r, onClick = { reason = r }) }
            }

            FieldRow(
                label = if (until != null) "Do dia" else "Data",
                value = "${weekdayName(date)}, ${formatDateLong(date)}",
                icon = Icons.Outlined.CalendarMonth,
                onClick = { dialog = BlockDialog.DATE }
            )
            if (!editing) {
                FieldRow(
                    label = "Repetir todos os dias até (opcional)",
                    value = if (until != null) {
                        val u = epochToDate(until)
                        "${weekdayName(u)}, ${formatDateLong(u)}"
                    } else {
                        "Só neste dia"
                    },
                    placeholder = until == null,
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { dialog = BlockDialog.UNTIL }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("Dia inteiro", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }

            if (!allDay) {
                SectionLabel("Período")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldRow(
                        label = "Início",
                        value = formatTime(startMin),
                        icon = Icons.Outlined.Schedule,
                        onClick = { dialog = BlockDialog.START },
                        modifier = Modifier.weight(1f)
                    )
                    FieldRow(
                        label = "Fim",
                        value = formatTime(endMin),
                        icon = Icons.Outlined.Schedule,
                        onClick = { dialog = BlockDialog.END },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!validTime) {
                    Text(
                        "O fim precisa ser depois do início.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Terracotta
                    )
                }
            }
            Text(
                "Horários bloqueados não aparecem como livres na agenda. O almoço de todos os dias é configurado em Mais > Horário de atendimento.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    when (dialog) {
        BlockDialog.NONE -> Unit
        BlockDialog.DATE -> DayPickerDialog(
            initialDay = day,
            onPick = {
                day = it
                dialog = BlockDialog.NONE
            },
            onDismiss = { dialog = BlockDialog.NONE }
        )

        BlockDialog.UNTIL -> DayPickerDialog(
            initialDay = untilDay ?: day,
            onPick = {
                untilDay = it
                dialog = BlockDialog.NONE
            },
            onDismiss = { dialog = BlockDialog.NONE }
        )

        BlockDialog.START -> MinutePickerDialog(
            initialMin = startMin,
            title = "Início",
            onPick = {
                startMin = it
                if (endMin <= it) endMin = (it + 60).coerceAtMost(24 * 60 - 1)
                dialog = BlockDialog.NONE
            },
            onDismiss = { dialog = BlockDialog.NONE }
        )

        BlockDialog.END -> MinutePickerDialog(
            initialMin = endMin,
            title = "Fim",
            onPick = {
                endMin = it
                dialog = BlockDialog.NONE
            },
            onDismiss = { dialog = BlockDialog.NONE }
        )

        BlockDialog.DELETE -> ConfirmDialog(
            title = "Excluir bloqueio",
            text = "O horário volta a ficar livre na agenda.",
            confirmLabel = "Excluir",
            danger = true,
            onConfirm = {
                dialog = BlockDialog.NONE
                scope.launch {
                    repo.deleteBlock(blockId)
                    notifier.show("Bloqueio excluído")
                    onDone()
                }
            },
            onDismiss = { dialog = BlockDialog.NONE }
        )
    }
}
