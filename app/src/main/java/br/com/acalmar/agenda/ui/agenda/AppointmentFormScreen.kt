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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.Service
import br.com.acalmar.agenda.data.WorkHours
import br.com.acalmar.agenda.domain.RepeatEvery
import br.com.acalmar.agenda.domain.busyRanges
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.findConflicts
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatDayMonthLong
import br.com.acalmar.agenda.domain.formatDuration
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.formatTimeRange
import br.com.acalmar.agenda.domain.freeStarts
import br.com.acalmar.agenda.domain.repeatDates
import br.com.acalmar.agenda.domain.weekdayName
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.clients.ClientPickerDialog
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.DayPickerDialog
import br.com.acalmar.agenda.ui.components.FieldRow
import br.com.acalmar.agenda.ui.components.MinutePickerDialog
import br.com.acalmar.agenda.ui.components.MoneyField
import br.com.acalmar.agenda.ui.components.PickerDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.nowMinutes
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch
import java.util.UUID

private enum class FormDialog { NONE, CLIENT, SERVICE, DATE, TIME, REPEAT }

private val DURATIONS = listOf(30, 45, 60, 75, 90, 120)

@Composable
fun AppointmentFormScreen(
    appointmentId: Long,
    initialDate: Long,
    initialStart: Int,
    initialClientId: Long,
    onDone: () -> Unit
) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val editing = appointmentId != 0L
    val today = todayEpochDay()

    var original by remember { mutableStateOf<Appointment?>(null) }
    var loaded by remember { mutableStateOf(!editing) }
    var clientId by remember { mutableLongStateOf(initialClientId) }
    var serviceId by remember { mutableStateOf<Long?>(null) }
    var serviceName by remember { mutableStateOf("") }
    var day by remember { mutableLongStateOf(if (initialDate >= 0) initialDate else today) }
    var startMin by remember { mutableIntStateOf(initialStart) }
    var durationMin by remember { mutableIntStateOf(60) }
    var priceCents by remember { mutableLongStateOf(0L) }
    var packageChoice by remember { mutableStateOf<Boolean?>(null) }
    var notes by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(RepeatEvery.NONE) }
    var repeatCount by remember { mutableIntStateOf(5) }
    var dialog by remember { mutableStateOf(FormDialog.NONE) }
    var repeatWarnings by remember { mutableStateOf<List<String>?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(appointmentId) {
        if (editing) {
            repo.getAppointment(appointmentId)?.let { a ->
                original = a
                clientId = a.clientId
                serviceId = a.serviceId
                serviceName = a.serviceName
                day = a.date
                startMin = a.startMin
                durationMin = a.durationMin
                priceCents = a.priceCents
                packageChoice = a.clientPackageId != null
                notes = a.notes
            }
            loaded = true
        }
    }

    val clients by repo.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val packageStates by repo.packageStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val hours by repo.workHours.collectAsStateWithLifecycle(initialValue = WorkHours())
    val dayAppts by remember(day) { repo.appointmentsBetween(day, day) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val dayBlocks by remember(day) { repo.blocksBetween(day, day) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val client = clients.firstOrNull { it.id == clientId }
    val date = epochToDate(day)
    val dayHours = hours.forDate(date)
    val lunch = hours.lunchFor(dayHours)
    val names = remember(clients) { clients.associate { it.id to it.name } }

    // Pacote da cliente que serve para este serviço.
    val eligible = packageStates.firstOrNull { s ->
        s.pkg.clientId == clientId && (
            s.pkg.id == original?.clientPackageId ||
                (serviceId != null && s.pkg.serviceId == serviceId && s.isActive(today) && s.toSchedule > 0)
            )
    }
    val usePackage = eligible != null && (packageChoice ?: true)
    val maxRepeat = if (usePackage && eligible != null) eligible.toSchedule.coerceAtLeast(1) else 30
    val count = repeatCount.coerceIn(2, maxOf(2, maxRepeat))

    val busy = busyRanges(dayAppts, dayBlocks, lunch, excludeApptId = original?.id)
    val starts = freeStarts(
        dayHours, hours.slotMin, durationMin, busy,
        notBefore = if (day == today) nowMinutes() else 0
    )
    val conflicts = if (startMin >= 0) {
        findConflicts(startMin, durationMin, dayAppts, dayBlocks, lunch, names, excludeApptId = original?.id)
    } else {
        emptyList()
    }
    val canSave = loaded && !saving && clientId != 0L && serviceName.isNotBlank() && startMin >= 0

    fun pickService(s: Service) {
        serviceId = s.id
        serviceName = s.name
        durationMin = s.durationMin
        priceCents = s.priceCents
        packageChoice = null
    }

    suspend fun save() {
        val pkgId = if (usePackage) eligible?.pkg?.id else null
        val price = if (usePackage) 0L else priceCents
        val current = original
        if (current != null) {
            repo.updateAppointment(
                current.copy(
                    clientId = clientId,
                    serviceId = serviceId,
                    serviceName = serviceName,
                    date = day,
                    startMin = startMin,
                    durationMin = durationMin,
                    priceCents = price,
                    clientPackageId = pkgId,
                    notes = notes.trim()
                )
            )
            notifier.show("Agendamento atualizado")
        } else {
            val dates = repeatDates(date, repeat, count)
            val seriesId = if (dates.size > 1) UUID.randomUUID().toString() else null
            repo.createAppointments(
                dates.map { d ->
                    Appointment(
                        clientId = clientId,
                        serviceId = serviceId,
                        serviceName = serviceName,
                        date = d.toEpochDay(),
                        startMin = startMin,
                        durationMin = durationMin,
                        priceCents = price,
                        clientPackageId = pkgId,
                        notes = notes.trim(),
                        seriesId = seriesId
                    )
                }
            )
            notifier.show(if (dates.size > 1) "${dates.size} sessões agendadas" else "Agendamento salvo")
        }
        onDone()
    }

    /** Nas sessões que se repetem, confere as datas seguintes antes de gravar. */
    suspend fun repeatConflicts(): List<String> {
        if (original != null || repeat == RepeatEvery.NONE) return emptyList()
        val out = mutableListOf<String>()
        repeatDates(date, repeat, count).drop(1).forEach { d ->
            val epoch = d.toEpochDay()
            val h = hours.forDate(d)
            val found = findConflicts(
                startMin, durationMin, repo.appointmentsOn(epoch), repo.blocksOn(epoch), hours.lunchFor(h), names
            )
            if (!h.open) {
                out += "${formatDateShort(d)} · dia sem atendimento"
            } else if (found.isNotEmpty()) {
                out += "${formatDateShort(d)} · ${found.first().what}"
            }
        }
        return out
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar(if (editing) "Editar agendamento" else "Novo agendamento", onBack = onDone) },
        bottomBar = {
            PrimaryButton(
                text = if (editing) "Salvar alterações" else "Agendar",
                enabled = canSave,
                onClick = {
                    saving = true
                    scope.launch {
                        val warnings = repeatConflicts()
                        if (warnings.isNotEmpty()) {
                            repeatWarnings = warnings
                            saving = false
                        } else {
                            save()
                        }
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
            FieldRow(
                label = "Cliente",
                value = client?.name ?: "Escolher cliente",
                placeholder = client == null,
                icon = Icons.Outlined.Person,
                onClick = { dialog = FormDialog.CLIENT }
            )
            FieldRow(
                label = "Serviço",
                value = serviceName.ifBlank { "Escolher serviço" },
                placeholder = serviceName.isBlank(),
                icon = Icons.Outlined.Spa,
                onClick = { dialog = FormDialog.SERVICE }
            )

            if (eligible != null) {
                SoftCard(container = Brand.BeigeSoft) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Usar sessão do pacote", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${eligible.pkg.serviceName} · ${eligible.used} de ${eligible.pkg.totalSessions} feitas",
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.BrownSoft
                            )
                        }
                        Switch(checked = usePackage, onCheckedChange = { packageChoice = it })
                    }
                }
            }

            FieldRow(
                label = "Data",
                value = "${weekdayName(date)}, ${formatDayMonthLong(date)} de ${date.year}",
                icon = Icons.Outlined.CalendarMonth,
                onClick = { dialog = FormDialog.DATE }
            )

            // ---- Horário
            SectionLabel("Horário", Modifier.padding(top = 4.dp))
            if (!dayHours.open) {
                Text(
                    "Este dia está sem atendimento no horário de trabalho. Ainda assim, dá para escolher um horário.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            } else if (starts.isEmpty()) {
                Text(
                    "Não há horário livre neste dia para ${formatDuration(durationMin)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (startMin >= 0 && startMin !in starts) {
                    ChoiceChip(formatTime(startMin), selected = true, onClick = { dialog = FormDialog.TIME })
                }
                starts.forEach { s ->
                    ChoiceChip(formatTime(s), selected = s == startMin, onClick = { startMin = s })
                }
                ChoiceChip("Outro horário", selected = false, onClick = { dialog = FormDialog.TIME })
            }
            if (startMin >= 0) {
                Text(
                    "Das ${formatTimeRange(startMin, startMin + durationMin)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Bronze,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (conflicts.isNotEmpty()) {
                SoftCard(container = Brand.TerracottaSoft, border = Brand.TerracottaSoft) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Este horário já está ocupado", fontWeight = FontWeight.SemiBold, color = Brand.Terracotta)
                            conflicts.forEach { c ->
                                Text(
                                    "${c.what} · ${formatTimeRange(c.startMin, c.endMin)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.Brown
                                )
                            }
                        }
                    }
                }
            }

            // ---- Duração
            SectionLabel("Duração", Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (DURATIONS + durationMin).distinct().sorted().forEach { d ->
                    ChoiceChip(formatDuration(d), selected = d == durationMin, onClick = { durationMin = d })
                }
            }

            // ---- Valor
            if (usePackage) {
                Text(
                    "Valor incluso no pacote.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
            } else {
                MoneyField(cents = priceCents, onChange = { priceCents = it }, label = "Valor", modifier = Modifier.fillMaxWidth())
            }

            // ---- Repetição (só ao criar)
            if (!editing) {
                FieldRow(
                    label = "Repetir",
                    value = if (repeat == RepeatEvery.NONE) repeat.label else "${repeat.label} · $count sessões",
                    icon = Icons.Outlined.Repeat,
                    onClick = { dialog = FormDialog.REPEAT }
                )
                if (repeat != RepeatEvery.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantas sessões", modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { repeatCount = (count - 1).coerceAtLeast(2) }) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Menos")
                        }
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        FilledTonalIconButton(onClick = { repeatCount = (count + 1).coerceAtMost(maxOf(2, maxRepeat)) }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Mais")
                        }
                    }
                    val last = repeatDates(date, repeat, count).last()
                    Text(
                        "Última sessão em ${formatDateShort(last)}." +
                            if (usePackage && eligible != null) " O pacote permite agendar até ${eligible.toSchedule}." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observações (opcional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            if (!usePackage && priceCents > 0 && repeat != RepeatEvery.NONE && !editing) {
                Text(
                    "Total das $count sessões: ${formatMoney(priceCents * count)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    when (dialog) {
        FormDialog.NONE -> Unit

        FormDialog.CLIENT -> ClientPickerDialog(
            clients = clients,
            onPick = {
                clientId = it
                packageChoice = null
                dialog = FormDialog.NONE
            },
            onDismiss = { dialog = FormDialog.NONE }
        )

        FormDialog.SERVICE -> PickerDialog(
            title = "Serviço",
            options = services.filter { it.active || it.id == serviceId },
            label = { it.name },
            sublabel = { "${formatDuration(it.durationMin)} · ${formatMoney(it.priceCents)}" },
            selected = services.firstOrNull { it.id == serviceId },
            onPick = {
                pickService(it)
                dialog = FormDialog.NONE
            },
            onDismiss = { dialog = FormDialog.NONE }
        )

        FormDialog.DATE -> DayPickerDialog(
            initialDay = day,
            onPick = {
                if (it != day) {
                    day = it
                    if (!editing) startMin = -1
                }
                dialog = FormDialog.NONE
            },
            onDismiss = { dialog = FormDialog.NONE }
        )

        FormDialog.TIME -> MinutePickerDialog(
            initialMin = if (startMin >= 0) startMin else dayHours.startMin,
            onPick = {
                startMin = it
                dialog = FormDialog.NONE
            },
            onDismiss = { dialog = FormDialog.NONE }
        )

        FormDialog.REPEAT -> PickerDialog(
            title = "Repetir",
            options = RepeatEvery.entries.toList(),
            label = { it.label },
            selected = repeat,
            onPick = {
                repeat = it
                dialog = FormDialog.NONE
            },
            onDismiss = { dialog = FormDialog.NONE }
        )
    }

    val warnings = repeatWarnings
    if (warnings != null) {
        AlertDialog(
            onDismissRequest = { repeatWarnings = null },
            title = { Text("Algumas datas já estão ocupadas") },
            text = {
                Column {
                    warnings.take(8).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (warnings.size > 8) Text("e mais ${warnings.size - 8}…", color = Brand.BrownSoft)
                    Spacer(Modifier.height(8.dp))
                    Text("Deseja agendar todas mesmo assim? Você pode ajustar cada uma depois.", color = Brand.BrownSoft)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repeatWarnings = null
                    saving = true
                    scope.launch { save() }
                }) { Text("Agendar assim mesmo", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { repeatWarnings = null }) { Text("Voltar") } }
        )
    }
}
