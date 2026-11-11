package br.com.acalmar.agenda.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.AnamnesisModel
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.QuoteStatus
import br.com.acalmar.agenda.domain.PackageState
import br.com.acalmar.agenda.domain.Receivable
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.formatPhone
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.friendlyDate
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.agenda.AppointmentSheet
import br.com.acalmar.agenda.ui.agenda.PaymentDialog
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.AlertChips
import br.com.acalmar.agenda.ui.components.Avatar
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.components.dialPhone
import br.com.acalmar.agenda.ui.components.openWhatsApp
import br.com.acalmar.agenda.ui.components.statusStyle
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ClientDetailScreen(
    clientId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSchedule: () -> Unit,
    onSellPackage: () -> Unit,
    onEditAppointment: (Long) -> Unit,
    onAnamnesis: () -> Unit,
    onConsent: () -> Unit,
    onPhotos: () -> Unit,
    onQuotes: () -> Unit
) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()

    val client by remember(clientId) { repo.client(clientId) }.collectAsStateWithLifecycle(initialValue = null)
    val allAppts by repo.appointments.collectAsStateWithLifecycle(initialValue = emptyList())
    val allStates by repo.packageStates.collectAsStateWithLifecycle(initialValue = emptyList())
    val allReceivables by repo.receivables.collectAsStateWithLifecycle(initialValue = emptyList())
    val anamnesis by remember(clientId) { repo.anamnesis(clientId) }.collectAsStateWithLifecycle(initialValue = null)
    val template by repo.anamnesisTemplate.collectAsStateWithLifecycle(initialValue = emptyList())
    val consents by remember(clientId) { repo.consents(clientId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val photos by remember(clientId) { repo.photos(clientId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val allQuotes by repo.quotes.collectAsStateWithLifecycle(initialValue = emptyList())

    var confirmDelete by remember { mutableStateOf(false) }
    var deletePackage by remember { mutableStateOf<PackageState?>(null) }
    var receiving by remember { mutableStateOf<Receivable?>(null) }
    var sheetApptId by remember { mutableStateOf<Long?>(null) }

    val appts = remember(allAppts, clientId) { allAppts.filter { it.clientId == clientId } }
    val upcoming = remember(appts, todayDay) {
        appts.filter { it.status == ApptStatus.AGENDADO && it.date >= todayDay }
            .sortedWith(compareBy<Appointment>({ it.date }, { it.startMin }))
    }
    val history = remember(appts, upcoming) {
        (appts - upcoming.toSet()).sortedWith(compareByDescending<Appointment> { it.date }.thenByDescending { it.startMin })
    }
    val states = remember(allStates, clientId, todayDay) {
        allStates.filter { it.pkg.clientId == clientId }
            .sortedWith(compareByDescending<PackageState> { it.isActive(todayDay) }.thenByDescending { it.pkg.purchasedOn })
    }
    val receivables = remember(allReceivables, clientId) { allReceivables.filter { it.clientId == clientId } }
    val answers = remember(anamnesis) { AnamnesisModel.parseAnswers(anamnesis?.answersJson) }
    val alerts = remember(template, answers) { AnamnesisModel.alerts(template, answers) }
    val quotes = remember(allQuotes, clientId) { allQuotes.filter { it.clientId == clientId } }
    val current = client

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = "Cliente",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar cliente", tint = Brand.Bronze)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Excluir cliente", tint = Brand.Terracotta)
                    }
                }
            )
        }
    ) { padding ->
        if (current == null) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Identificação e atalhos
            item(key = "header") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(current.name, size = 64)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.name, style = MaterialTheme.typography.headlineSmall)
                            if (current.phone.isNotBlank()) {
                                Text(formatPhone(current.phone), style = MaterialTheme.typography.bodyLarge, color = Brand.BrownSoft)
                            }
                        }
                    }
                    if (alerts.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        AlertChips(alerts)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSchedule,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Agendar")
                        }
                        if (current.phone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    if (!openWhatsApp(context, current.phone)) notifier.show("Confira o número: precisa ter DDD")
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WhatsApp", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { dialPhone(context, current.phone) },
                                modifier = Modifier.width(60.dp),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.Phone, contentDescription = "Ligar", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // ---- Ficha: anamnese, termo, fotos e orçamentos
            item(key = "ficha") {
                val lastConsent = consents.firstOrNull()
                val openQuotes = quotes.count { it.status == QuoteStatus.ABERTO }
                SoftCard(Modifier.fillMaxWidth()) {
                    Column {
                        RecordRow(
                            icon = Icons.Outlined.HealthAndSafety,
                            title = "Ficha de anamnese",
                            status = anamnesis?.let { a ->
                                val count = AnamnesisModel.answeredCount(template, answers)
                                "Preenchida em ${formatDateShort(dateOfMillis(a.updatedAt))} · " +
                                    if (count == 1) "1 resposta" else "$count respostas"
                            } ?: "Ainda não preenchida",
                            pending = anamnesis == null,
                            onClick = onAnamnesis
                        )
                        HorizontalDivider(color = Brand.Line)
                        RecordRow(
                            icon = Icons.Outlined.Gesture,
                            title = "Termo de consentimento",
                            status = lastConsent?.let { c ->
                                "Assinado em ${formatDateShort(epochToDate(c.signedOn))} · " +
                                    if (c.imageUse) "autoriza fotos" else "não autoriza fotos"
                            } ?: "Ainda não assinado",
                            pending = lastConsent == null,
                            onClick = onConsent
                        )
                        HorizontalDivider(color = Brand.Line)
                        RecordRow(
                            icon = Icons.Outlined.PhotoLibrary,
                            title = "Fotos de antes e depois",
                            status = when (photos.size) {
                                0 -> "Nenhuma foto"
                                1 -> "1 foto"
                                else -> "${photos.size} fotos"
                            },
                            onClick = onPhotos
                        )
                        HorizontalDivider(color = Brand.Line)
                        RecordRow(
                            icon = Icons.Outlined.RequestQuote,
                            title = "Orçamentos",
                            status = when {
                                quotes.isEmpty() -> "Nenhum orçamento"
                                quotes.size == 1 -> if (openQuotes == 1) "1 orçamento em aberto" else "1 orçamento"
                                openQuotes > 0 -> "${quotes.size} orçamentos · $openQuotes em aberto"
                                else -> "${quotes.size} orçamentos"
                            },
                            onClick = onQuotes
                        )
                    }
                }
            }

            // ---- Dados
            if (current.birthday.isNotBlank() || current.howFound.isNotBlank() || current.notes.isNotBlank()) {
                item(key = "info") {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current.birthday.isNotBlank()) InfoLine("Aniversário", current.birthday)
                            if (current.howFound.isNotBlank()) InfoLine("Como conheceu", current.howFound)
                            if (current.notes.isNotBlank()) InfoLine("Observações", current.notes)
                        }
                    }
                }
            }

            // ---- A receber
            if (receivables.isNotEmpty()) {
                item(key = "due-title") { SectionLabel("A receber", Modifier.padding(top = 8.dp)) }
                items(receivables, key = { "r${it.appointmentId}-${it.clientPackageId}" }) { r ->
                    SoftCard(Modifier.fillMaxWidth(), container = Brand.TerracottaSoft, border = Brand.TerracottaSoft) {
                        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatDateShort(epochToDate(r.date))} · ${formatMoney(r.dueCents)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Brand.Terracotta,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = { receiving = r }) { Text("Receber") }
                        }
                    }
                }
            }

            // ---- Pacotes
            item(key = "pkg-title") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    SectionLabel("Pacotes", Modifier.weight(1f))
                    TextButton(onClick = onSellPackage) { Text("Vender pacote") }
                }
            }
            if (states.isEmpty()) {
                item(key = "pkg-empty") {
                    Text("Nenhum pacote.", style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft)
                }
            }
            items(states, key = { "p${it.pkg.id}" }) { state ->
                PackageCard(state, todayDay, onDelete = { deletePackage = state })
            }

            // ---- Agendamentos
            item(key = "next-title") { SectionLabel("Próximos horários", Modifier.padding(top = 8.dp)) }
            if (upcoming.isEmpty()) {
                item(key = "next-empty") {
                    Text("Nenhum horário agendado.", style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft)
                }
            }
            items(upcoming, key = { "u${it.id}" }) { a ->
                HistoryRow(a, today, onClick = { sheetApptId = a.id })
            }
            if (history.isNotEmpty()) {
                item(key = "hist-title") { SectionLabel("Histórico e evolução", Modifier.padding(top = 8.dp)) }
                items(history.take(60), key = { "h${it.id}" }) { a ->
                    HistoryRow(a, today, onClick = { sheetApptId = a.id })
                }
            }
        }
    }

    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = "Excluir ${current.name}?",
            text = "Os agendamentos e pacotes desta cliente também serão apagados. Os lançamentos do caixa são mantidos. Esta ação não pode ser desfeita.",
            confirmLabel = "Excluir",
            danger = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    repo.deleteClient(clientId)
                    notifier.show("Cliente excluída")
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }

    deletePackage?.let { state ->
        ConfirmDialog(
            title = "Excluir pacote?",
            text = "O pacote de ${state.pkg.serviceName} será apagado. As sessões já feitas continuam no histórico e os pagamentos continuam no caixa.",
            confirmLabel = "Excluir",
            danger = true,
            onConfirm = {
                deletePackage = null
                scope.launch {
                    repo.deleteClientPackage(state.pkg.id)
                    notifier.show("Pacote excluído")
                }
            },
            onDismiss = { deletePackage = null }
        )
    }

    receiving?.let { r ->
        PaymentDialog(
            title = "Receber pagamento",
            suggestedCents = r.dueCents,
            allowLater = false,
            confirmLabel = "Receber",
            onDismiss = { receiving = null },
            onConfirm = { amount, method ->
                receiving = null
                scope.launch {
                    repo.receive(r, amount, method, todayDay)
                    if (amount > 0) notifier.show("Pagamento lançado no caixa")
                }
            }
        )
    }

    val sheetAppt = sheetApptId?.let { id -> appts.firstOrNull { it.id == id } }
    if (sheetAppt != null) {
        AppointmentSheet(
            appt = sheetAppt,
            client = current,
            packageState = allStates.firstOrNull { it.pkg.id == sheetAppt.clientPackageId },
            onDismiss = { sheetApptId = null },
            onEdit = {
                sheetApptId = null
                onEditAppointment(sheetAppt.id)
            },
            alerts = alerts
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.BrownSoft)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PackageCard(state: PackageState, todayDay: Long, onDelete: () -> Unit) {
    val pkg = state.pkg
    val active = state.isActive(todayDay)
    SoftCard(Modifier.fillMaxWidth(), container = if (active) Brand.Card else Brand.GraySoft) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pkg.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when {
                    state.remaining == 0 -> Tag("Concluído", Brand.SageSoft, Brand.Sage)
                    state.isExpired(todayDay) -> Tag("Vencido", Brand.TerracottaSoft, Brand.Terracotta)
                    else -> Tag("Ativo", Brand.Beige, Brand.BronzeDark)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Excluir pacote", tint = Brand.Gray, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(end = 10.dp)) {
                Text(
                    "${state.used} de ${pkg.totalSessions} sessões feitas" +
                        if (state.scheduled > 0) " · ${state.scheduled} agendada${if (state.scheduled > 1) "s" else ""}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (pkg.totalSessions > 0) state.used.toFloat() / pkg.totalSessions else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Brand.Bronze,
                    trackColor = Brand.Beige,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(8.dp))
                val parts = buildList {
                    add("Comprado em ${formatDateShort(epochToDate(pkg.purchasedOn))}")
                    pkg.expiresOn?.let { add("vale até ${formatDateShort(epochToDate(it))}") }
                    add(formatMoney(pkg.priceCents))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
                if (state.dueCents > 0) {
                    Text(
                        "Falta receber ${formatMoney(state.dueCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Terracotta,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(appt: Appointment, today: LocalDate, onClick: () -> Unit) {
    val style = statusStyle(appt.status)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(top = 8.dp, bottom = if (appt.evolution.isBlank()) 8.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(appt.serviceName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${friendlyDate(epochToDate(appt.date), today)} · ${formatTime(appt.startMin)}" +
                        if (appt.clientPackageId != null) " · pacote" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
            }
            Tag(style.label, style.background, style.content)
        }
        if (appt.evolution.isNotBlank()) {
            Text(
                appt.evolution,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.Brown,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        HorizontalDivider(color = Brand.Line)
    }
}

/** Linha do quadro da ficha: ícone, título, situação e seta. */
@Composable
private fun RecordRow(icon: ImageVector, title: String, status: String, onClick: () -> Unit, pending: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (pending) Brand.Terracotta else Brand.BrownSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Brand.Gold)
    }
}

/** Data (dia) de um instante gravado em milissegundos, no fuso do celular. */
private fun dateOfMillis(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
