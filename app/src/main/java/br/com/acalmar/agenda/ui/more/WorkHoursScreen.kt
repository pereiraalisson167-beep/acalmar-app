package br.com.acalmar.agenda.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.DayHours
import br.com.acalmar.agenda.data.WorkHours
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.weekdayNameOfIndex
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.MinutePickerDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

/** Qual horário está sendo editado no relógio. */
private data class TimeTarget(val dayIndex: Int, val isStart: Boolean)

private const val LUNCH = -1

@Composable
fun WorkHoursScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    var hours by remember { mutableStateOf<WorkHours?>(null) }
    var target by remember { mutableStateOf<TimeTarget?>(null) }

    LaunchedEffect(Unit) { hours = repo.getWorkHours() }

    val current = hours
    val valid = current != null &&
        current.days.all { !it.open || it.endMin > it.startMin } &&
        (!current.lunchEnabled || current.lunchEnd > current.lunchStart)

    fun updateDay(index: Int, change: (DayHours) -> DayHours) {
        val h = hours ?: return
        hours = h.copy(days = h.days.mapIndexed { i, d -> if (i == index) change(d) else d })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Horário de atendimento", onBack = onBack) },
        bottomBar = {
            PrimaryButton(
                text = "Salvar",
                enabled = valid,
                onClick = {
                    val h = hours ?: return@PrimaryButton
                    scope.launch {
                        repo.saveWorkHours(h)
                        notifier.show("Horário de atendimento salvo")
                        onBack()
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    ) { padding ->
        if (current == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("Dias de trabalho")
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    current.days.forEachIndexed { index, day ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(56.dp)) {
                            Text(
                                weekdayNameOfIndex(index),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (day.open) Brand.Brown else Brand.BrownSoft,
                                modifier = Modifier.weight(1f)
                            )
                            if (day.open) {
                                TextButton(onClick = { target = TimeTarget(index, true) }) {
                                    Text(formatTime(day.startMin), fontWeight = FontWeight.SemiBold)
                                }
                                Text("às", style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
                                TextButton(onClick = { target = TimeTarget(index, false) }) {
                                    Text(formatTime(day.endMin), fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Text(
                                    "Fechado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Brand.BrownSoft,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                            Switch(checked = day.open, onCheckedChange = { open -> updateDay(index) { it.copy(open = open) } })
                        }
                        if (index < current.days.lastIndex) HorizontalDivider(color = Brand.Line)
                    }
                }
            }

            SectionLabel("Almoço", Modifier.padding(top = 4.dp))
            SoftCard(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .height(56.dp)
                ) {
                    Text("Pausa para o almoço", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (current.lunchEnabled) {
                        TextButton(onClick = { target = TimeTarget(LUNCH, true) }) {
                            Text(formatTime(current.lunchStart), fontWeight = FontWeight.SemiBold)
                        }
                        Text("às", style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
                        TextButton(onClick = { target = TimeTarget(LUNCH, false) }) {
                            Text(formatTime(current.lunchEnd), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Switch(checked = current.lunchEnabled, onCheckedChange = { hours = current.copy(lunchEnabled = it) })
                }
            }

            SectionLabel("Horários oferecidos ao agendar", Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "A cada 15 min", 30 to "A cada 30 min", 60 to "A cada 1 hora").forEach { (min, label) ->
                    ChoiceChip(label, selected = current.slotMin == min, onClick = { hours = current.copy(slotMin = min) })
                }
            }
            if (!valid) {
                Text(
                    "Confira os horários: o fim precisa ser depois do início.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Terracotta
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    val t = target
    if (t != null && current != null) {
        val initial = when {
            t.dayIndex == LUNCH -> if (t.isStart) current.lunchStart else current.lunchEnd
            t.isStart -> current.days[t.dayIndex].startMin
            else -> current.days[t.dayIndex].endMin
        }
        MinutePickerDialog(
            initialMin = initial,
            title = if (t.isStart) "Início" else "Fim",
            onPick = { min ->
                when {
                    t.dayIndex == LUNCH && t.isStart -> hours = current.copy(lunchStart = min)
                    t.dayIndex == LUNCH -> hours = current.copy(lunchEnd = min)
                    t.isStart -> updateDay(t.dayIndex) { it.copy(startMin = min) }
                    else -> updateDay(t.dayIndex) { it.copy(endMin = min) }
                }
                target = null
            },
            onDismiss = { target = null }
        )
    }
}
