package br.com.acalmar.agenda.ui.clients

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.domain.RecallItem
import br.com.acalmar.agenda.domain.daysAgoLabel
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatPhone
import br.com.acalmar.agenda.domain.recallDueLabel
import br.com.acalmar.agenda.domain.recallMessage
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.Avatar
import br.com.acalmar.agenda.ui.components.EmptyState
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.openWhatsApp
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

/** Hora de chamar de volta: clientes que passaram do tempo de retorno e não têm horário marcado. */
@Composable
fun RecallScreen(onBack: () -> Unit, onOpenClient: (Long) -> Unit, onSchedule: (Long) -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = todayEpochDay()
    val recalls by remember(today) { repo.recalls(today) }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Chamar de volta", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "intro") {
                Text(
                    "Clientes que já passaram, ou estão perto, do tempo de retorno do último atendimento e ainda não têm novo horário. " +
                        "O tempo de retorno de cada serviço é definido em Mais > Serviços e pacotes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            }
            if (recalls.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Ninguém para chamar agora",
                        subtitle = "Quando chegar a hora do retorno de uma cliente, ela aparece aqui."
                    )
                }
            }
            items(recalls, key = { it.client.id }) { item ->
                RecallRow(
                    item = item,
                    today = today,
                    onOpenClient = { onOpenClient(item.client.id) },
                    onSchedule = { onSchedule(item.client.id) },
                    onWhatsApp = {
                        if (!openWhatsApp(context, item.client.phone, recallMessage(item, today))) {
                            notifier.show("Confira o telefone da cliente: precisa ter DDD")
                        }
                    },
                    onSnooze = {
                        scope.launch {
                            repo.snoozeRecall(item.client.id, today + 7)
                            notifier.show("Lembrete adiado por 7 dias")
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            repo.dismissRecall(item.client.id, item.lastDate)
                            notifier.show("Retirada da lista até o próximo atendimento")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RecallRow(
    item: RecallItem,
    today: Long,
    onOpenClient: () -> Unit,
    onSchedule: () -> Unit,
    onWhatsApp: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val late = item.dueDate < today
    SoftCard(Modifier.fillMaxWidth(), onClick = onOpenClient) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(item.client.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.client.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.client.phone.isNotBlank()) {
                        Text(formatPhone(item.client.phone), style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Mais opções", tint = Brand.Gray)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Lembrar em 7 dias") },
                            onClick = {
                                menuOpen = false
                                onSnooze()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Não chamar desta vez") },
                            onClick = {
                                menuOpen = false
                                onDismiss()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(end = 10.dp)) {
                Text(
                    "${item.serviceName} · ${formatDateShort(epochToDate(item.lastDate))} (${daysAgoLabel(item.lastDate, today)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
                Text(
                    "Retorno: ${formatDateShort(epochToDate(item.dueDate))} · ${recallDueLabel(item.dueDate, today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (late) Brand.Terracotta else Brand.Bronze,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.client.phone.isNotBlank()) {
                        Button(
                            onClick = onWhatsApp,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("WhatsApp", maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = onSchedule,
                        modifier = Modifier.weight(if (item.client.phone.isNotBlank()) 0.7f else 1f),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Agendar", maxLines = 1)
                    }
                }
            }
        }
    }
}
