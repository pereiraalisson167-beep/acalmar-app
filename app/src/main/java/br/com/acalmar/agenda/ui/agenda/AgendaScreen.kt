package br.com.acalmar.agenda.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.data.WorkHours
import br.com.acalmar.agenda.domain.DAY_MIN
import br.com.acalmar.agenda.domain.DayItem
import br.com.acalmar.agenda.domain.buildDay
import br.com.acalmar.agenda.domain.formatDayMonthLong
import br.com.acalmar.agenda.domain.formatDuration
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.formatMonthYear
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.formatTimeRange
import br.com.acalmar.agenda.domain.relativeLabel
import br.com.acalmar.agenda.domain.roundUpTo
import br.com.acalmar.agenda.domain.weekStart
import br.com.acalmar.agenda.domain.weekdayName
import br.com.acalmar.agenda.domain.weekdayShort
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.DayPickerDialog
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.components.nowMinutes
import br.com.acalmar.agenda.ui.components.statusStyle
import br.com.acalmar.agenda.ui.theme.Brand
import java.time.LocalDate

@Composable
fun AgendaScreen(
    onNewAppointment: (date: Long, start: Int) -> Unit,
    onEditAppointment: (Long) -> Unit,
    onNewBlock: (date: Long, start: Int) -> Unit,
    onEditBlock: (Long) -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenHours: () -> Unit
) {
    val repo = LocalRepo.current
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    var selectedDay by rememberSaveable { mutableLongStateOf(todayDay) }
    var sheetApptId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(selectedDay)
    val weekFirst = weekStart(date).toEpochDay()

    val weekAppts by remember(weekFirst) { repo.appointmentsBetween(weekFirst, weekFirst + 6) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val weekBlocks by remember(weekFirst) { repo.blocksBetween(weekFirst, weekFirst + 6) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val clients by repo.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    val hours by repo.workHours.collectAsStateWithLifecycle(initialValue = WorkHours())
    val packageStates by repo.packageStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val alertsByClient by repo.alertsByClient.collectAsStateWithLifecycle(initialValue = emptyMap())

    val clientById = remember(clients) { clients.associateBy { it.id } }
    val dayAppts = weekAppts.filter { it.date == selectedDay }
    val dayBlocks = weekBlocks.filter { it.date == selectedDay }
    val dayHours = hours.forDate(date)
    val hideFreeBefore = when {
        selectedDay < todayDay -> DAY_MIN
        selectedDay == todayDay -> roundUpTo(nowMinutes(), 15)
        else -> 0
    }
    val dayItems = buildDay(dayHours, hours.lunchFor(dayHours), dayAppts, dayBlocks, hideFreeBefore = hideFreeBefore)
    val active = dayAppts.filter { it.status == ApptStatus.AGENDADO || it.status == ApptStatus.CONCLUIDO }
    val expected = active.filter { it.clientPackageId == null }.sumOf { it.priceCents }
    val busyDays = remember(weekAppts) {
        weekAppts.filter { it.status == ApptStatus.AGENDADO || it.status == ApptStatus.CONCLUIDO }.map { it.date }.toSet()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- Cabeçalho
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(formatMonthYear(date), style = MaterialTheme.typography.headlineSmall)
                    val relative = relativeLabel(date, today)
                    Text(
                        (if (relative != null) "$relative · " else "") + "${weekdayName(date)}, ${formatDayMonthLong(date)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft
                    )
                }
                if (selectedDay != todayDay) {
                    TextButton(onClick = { selectedDay = todayDay }) { Text("Hoje") }
                }
                IconButton(onClick = { pickingDate = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Escolher data", tint = Brand.Bronze)
                }
            }

            // ---- Semana
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedDay -= 7 }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Semana anterior", tint = Brand.Bronze)
                }
                for (i in 0..6) {
                    val day = weekFirst + i
                    DayCell(
                        date = LocalDate.ofEpochDay(day),
                        selected = day == selectedDay,
                        isToday = day == todayDay,
                        hasAppointments = day in busyDays,
                        closed = !hours.forDate(LocalDate.ofEpochDay(day)).open,
                        onClick = { selectedDay = day },
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = { selectedDay += 7 }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Próxima semana", tint = Brand.Bronze)
                }
            }

            // ---- Resumo do dia
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val summary = when (active.size) {
                    0 -> "Nenhum atendimento"
                    1 -> "1 atendimento"
                    else -> "${active.size} atendimentos"
                }
                Text(summary, style = MaterialTheme.typography.labelLarge, color = Brand.Bronze, modifier = Modifier.weight(1f))
                if (expected > 0) {
                    Text(formatMoney(expected), style = MaterialTheme.typography.labelLarge, color = Brand.BrownSoft)
                }
            }

            // ---- Lista do dia
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!dayHours.open) {
                    item(key = "closed") {
                        ClosedDayNote(hasItems = dayItems.isNotEmpty(), onOpenHours = onOpenHours)
                    }
                }
                items(dayItems, key = { item ->
                    when (item) {
                        is DayItem.Appt -> "a${item.appt.id}"
                        is DayItem.Blocked -> "b${item.block?.id ?: -1}-${item.startMin}"
                        is DayItem.Free -> "f${item.startMin}"
                    }
                }) { item ->
                    when (item) {
                        is DayItem.Appt -> AppointmentCard(
                            appt = item.appt,
                            client = clientById[item.appt.clientId],
                            hasAlerts = alertsByClient[item.appt.clientId].orEmpty().isNotEmpty(),
                            onClick = { sheetApptId = item.appt.id }
                        )

                        is DayItem.Blocked -> BlockedCard(
                            item = item,
                            onClick = item.block?.let { b -> { onEditBlock(b.id) } } ?: onOpenHours
                        )

                        is DayItem.Free -> FreeSlot(
                            item = item,
                            onClick = { onNewAppointment(selectedDay, item.startMin) }
                        )
                    }
                }
                if (dayHours.open && dayItems.isEmpty()) {
                    item(key = "past-empty") {
                        Text(
                            if (selectedDay == todayDay) "Sem horários livres pelo resto do dia." else "Nenhum registro neste dia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.BrownSoft,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }

        // ---- Botões de ação
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { onNewBlock(selectedDay, -1) },
                containerColor = Brand.Beige,
                contentColor = Brand.BronzeDark
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = "Bloquear horário")
            }
            ExtendedFloatingActionButton(
                onClick = { onNewAppointment(selectedDay, -1) },
                containerColor = Brand.Bronze,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Agendar") },
                text = { Text("Agendar") }
            )
        }
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

    val sheetAppt = sheetApptId?.let { id -> weekAppts.firstOrNull { it.id == id } }
    if (sheetAppt != null) {
        AppointmentSheet(
            appt = sheetAppt,
            client = clientById[sheetAppt.clientId],
            packageState = packageStates.firstOrNull { it.pkg.id == sheetAppt.clientPackageId },
            onDismiss = { sheetApptId = null },
            onEdit = {
                sheetApptId = null
                onEditAppointment(sheetAppt.id)
            },
            onOpenClient = {
                sheetApptId = null
                onOpenClient(sheetAppt.clientId)
            },
            alerts = alertsByClient[sheetAppt.clientId].orEmpty()
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    hasAppointments: Boolean,
    closed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            weekdayShort(date).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Brand.Bronze else Brand.BrownSoft
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (selected) Brand.Bronze else Color.Transparent)
                .then(
                    if (isToday && !selected) Modifier.border(1.5.dp, Brand.Gold, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> Color.White
                    closed -> Brand.Gold
                    else -> Brand.Brown
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (hasAppointments) Brand.Bronze else Color.Transparent)
        )
    }
}

@Composable
private fun AppointmentCard(appt: Appointment, client: Client?, hasAlerts: Boolean, onClick: () -> Unit) {
    val style = statusStyle(appt.status)
    val faded = appt.status == ApptStatus.FALTOU
    SoftCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                Text(formatTime(appt.startMin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    formatTime(appt.startMin + appt.durationMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .width(3.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(style.content)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    client?.name ?: "Cliente",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (faded) Brand.BrownSoft else Brand.Brown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    appt.serviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (appt.status != ApptStatus.AGENDADO || appt.clientPackageId != null || hasAlerts) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (appt.status != ApptStatus.AGENDADO) Tag(style.label, style.background, style.content)
                        if (appt.clientPackageId != null) Tag("Pacote", Brand.Beige, Brand.BronzeDark)
                        if (hasAlerts) Tag("Atenção", Brand.TerracottaSoft, Brand.Terracotta)
                    }
                }
            }
            if (appt.clientPackageId == null && appt.priceCents > 0) {
                Text(
                    formatMoney(appt.priceCents),
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand.Bronze
                )
            }
        }
    }
}

@Composable
private fun BlockedCard(item: DayItem.Blocked, onClick: () -> Unit) {
    SoftCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), container = Brand.GraySoft, border = Brand.GraySoft) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Brand.Gray, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                item.label.ifBlank { "Horário bloqueado" },
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.Gray,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (item.allDay) "Dia inteiro" else formatTimeRange(item.startMin, item.endMin),
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Gray
            )
        }
    }
}

@Composable
private fun FreeSlot(item: DayItem.Free, onClick: () -> Unit) {
    val dash = Brand.Gold
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Livre · ${formatTimeRange(item.startMin, item.endMin)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.Bronze,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatDuration(item.endMin - item.startMin),
            style = MaterialTheme.typography.bodySmall,
            color = Brand.BrownSoft
        )
    }
}

@Composable
private fun ClosedDayNote(hasItems: Boolean, onOpenHours: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (hasItems) 8.dp else 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Spa, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(if (hasItems) 24.dp else 44.dp))
        Spacer(Modifier.height(6.dp))
        Text("Dia sem atendimento", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onOpenHours) { Text("Alterar horário de atendimento") }
    }
}
