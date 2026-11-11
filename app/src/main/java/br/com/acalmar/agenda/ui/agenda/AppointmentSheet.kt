package br.com.acalmar.agenda.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.Appointment
import br.com.acalmar.agenda.data.ApptStatus
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.data.PayMethod
import br.com.acalmar.agenda.data.PaymentInput
import br.com.acalmar.agenda.domain.PackageState
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.firstName
import br.com.acalmar.agenda.domain.formatDayMonthLong
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.formatPhone
import br.com.acalmar.agenda.domain.formatTime
import br.com.acalmar.agenda.domain.formatTimeRange
import br.com.acalmar.agenda.domain.weekdayName
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AlertChips
import br.com.acalmar.agenda.ui.components.Avatar
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.MoneyField
import br.com.acalmar.agenda.ui.components.PayMethodChips
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.components.openWhatsApp
import br.com.acalmar.agenda.ui.components.statusStyle
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

private enum class SheetDialog { NONE, CONCLUDE, NO_SHOW, UNSCHEDULE, DELETE, EVOLUTION }

/**
 * Detalhes de um atendimento, com as ações do dia a dia: concluir, faltou, desmarcar, editar
 * e anotar a evolução da sessão. `alerts` são os avisos de atenção da ficha de anamnese.
 */
@Composable
fun AppointmentSheet(
    appt: Appointment,
    client: Client?,
    packageState: PackageState?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onOpenClient: (() -> Unit)? = null,
    alerts: List<String> = emptyList()
) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dialog by remember { mutableStateOf(SheetDialog.NONE) }
    var following by remember { mutableIntStateOf(0) }
    val date = epochToDate(appt.date)
    val clientName = client?.name ?: "Cliente"
    val usesPackage = appt.clientPackageId != null

    LaunchedEffect(appt.id) { following = repo.countFollowing(appt) }

    fun act(message: String, block: suspend () -> Unit) {
        scope.launch {
            block()
            notifier.show(message)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Brand.Cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(clientName, size = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(clientName, style = MaterialTheme.typography.titleLarge)
                    if (!client?.phone.isNullOrBlank()) {
                        Text(
                            formatPhone(client?.phone.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.BrownSoft
                        )
                    }
                }
                val style = statusStyle(appt.status)
                Tag(style.label, style.background, style.content)
            }
            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AlertChips(alerts)
            }

            Spacer(Modifier.height(16.dp))
            Text(appt.serviceName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${weekdayName(date)}, ${formatDayMonthLong(date)} · ${formatTimeRange(appt.startMin, appt.startMin + appt.durationMin)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.BrownSoft
            )
            Spacer(Modifier.height(6.dp))
            if (usesPackage) {
                val info = if (packageState != null) {
                    "Sessão do pacote · ${packageState.used} de ${packageState.pkg.totalSessions} feitas"
                } else {
                    "Sessão do pacote"
                }
                Text(info, style = MaterialTheme.typography.bodyMedium, color = Brand.Bronze, fontWeight = FontWeight.SemiBold)
            } else {
                Text(formatMoney(appt.priceCents), style = MaterialTheme.typography.titleMedium, color = Brand.Bronze)
            }
            if (appt.seriesId != null) {
                Text(
                    "Faz parte de sessões que se repetem",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            }
            if (appt.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(appt.notes, style = MaterialTheme.typography.bodyMedium)
            }

            // Evolução: anotação do que foi feito e de como a cliente respondeu.
            if (appt.status == ApptStatus.CONCLUIDO || appt.evolution.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Evolução da sessão", Modifier.weight(1f))
                    TextButton(onClick = { dialog = SheetDialog.EVOLUTION }) {
                        Text(if (appt.evolution.isBlank()) "Anotar" else "Alterar")
                    }
                }
                Text(
                    appt.evolution.ifBlank { "Nenhuma anotação nesta sessão." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (appt.evolution.isBlank()) Brand.BrownSoft else Brand.Brown
                )
            }

            Spacer(Modifier.height(18.dp))

            if (appt.status == ApptStatus.AGENDADO) {
                Button(
                    onClick = { dialog = SheetDialog.CONCLUDE },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Concluir atendimento", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetAction("Desmarcou", Icons.Outlined.EventBusy, Modifier.weight(1f)) {
                        if (following > 0) dialog = SheetDialog.UNSCHEDULE
                        else act("Horário liberado") { repo.unschedule(appt, andFollowing = false) }
                    }
                    SheetAction("Faltou", Icons.Outlined.PersonOff, Modifier.weight(1f), tint = Brand.Terracotta) {
                        if (usesPackage) dialog = SheetDialog.NO_SHOW
                        else act("Falta registrada") { repo.markNoShow(appt, chargePackage = false) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                SheetAction("Voltar para agendado", Icons.Outlined.Replay, Modifier.fillMaxWidth()) {
                    act("Atendimento reaberto") { repo.reopen(appt) }
                }
                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider(color = Brand.Line)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                if (!client?.phone.isNullOrBlank()) {
                    TextButton(onClick = {
                        val text = "Olá, ${firstName(clientName)}! Passando para lembrar do seu horário: " +
                            "${appt.serviceName}, ${weekdayName(date).lowercase()}, ${formatDayMonthLong(date)}, às ${formatTime(appt.startMin)}."
                        if (!openWhatsApp(context, client?.phone.orEmpty(), text)) {
                            notifier.show("Não foi possível abrir o WhatsApp")
                        }
                    }) {
                        Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Editar")
                }
                TextButton(onClick = { dialog = SheetDialog.DELETE }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Brand.Terracotta, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excluir", color = Brand.Terracotta)
                }
            }
            if (onOpenClient != null && client != null) {
                TextButton(onClick = onOpenClient, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Ver ficha de ${firstName(clientName)}")
                }
            }
        }
    }

    when (dialog) {
        SheetDialog.NONE -> Unit

        SheetDialog.CONCLUDE -> ConcludeDialog(
            usesPackage = usesPackage,
            suggestedCents = appt.priceCents,
            initialEvolution = appt.evolution,
            onDismiss = { dialog = SheetDialog.NONE },
            onConfirm = { amount, method, evolution ->
                dialog = SheetDialog.NONE
                val payment = if (amount > 0) PaymentInput(amount, method, todayEpochDay()) else null
                val message = when {
                    usesPackage -> "Atendimento concluído"
                    payment != null -> "Atendimento concluído e pagamento lançado no caixa"
                    else -> "Atendimento concluído · pagamento pendente"
                }
                act(message) { repo.conclude(appt, payment, evolution) }
            }
        )

        SheetDialog.EVOLUTION -> EvolutionDialog(
            initial = appt.evolution,
            onDismiss = { dialog = SheetDialog.NONE },
            onSave = { text ->
                dialog = SheetDialog.NONE
                scope.launch {
                    repo.setEvolution(appt.id, text)
                    notifier.show("Evolução anotada")
                }
            }
        )

        SheetDialog.NO_SHOW -> AlertDialog(
            onDismissRequest = { dialog = SheetDialog.NONE },
            title = { Text("Descontar a sessão do pacote?") },
            text = { Text("Se a cliente não avisou com 24 horas de antecedência, a sessão é descontada do pacote.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = SheetDialog.NONE
                    act("Falta registrada · sessão descontada") { repo.markNoShow(appt, chargePackage = true) }
                }) { Text("Descontar", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog = SheetDialog.NONE
                    act("Falta registrada · sessão mantida") { repo.markNoShow(appt, chargePackage = false) }
                }) { Text("Não descontar") }
            }
        )

        SheetDialog.UNSCHEDULE -> AlertDialog(
            onDismissRequest = { dialog = SheetDialog.NONE },
            title = { Text("Desmarcar") },
            text = { Text("Este horário se repete. Há mais $following ${if (following == 1) "sessão agendada" else "sessões agendadas"} depois desta.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = SheetDialog.NONE
                    act("Horário liberado") { repo.unschedule(appt, andFollowing = false) }
                }) { Text("Só esta", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog = SheetDialog.NONE
                    act("Horários liberados") { repo.unschedule(appt, andFollowing = true) }
                }) { Text("Esta e as próximas") }
            }
        )

        SheetDialog.DELETE -> if (following > 0) {
            AlertDialog(
                onDismissRequest = { dialog = SheetDialog.NONE },
                title = { Text("Excluir agendamento") },
                text = { Text("Este horário se repete. Há mais $following ${if (following == 1) "sessão agendada" else "sessões agendadas"} depois desta. O que foi excluído não volta.") },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = SheetDialog.NONE
                        act("Agendamento excluído") { repo.deleteAppointment(appt, andFollowing = false) }
                    }) { Text("Só esta", color = Brand.Terracotta, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialog = SheetDialog.NONE
                        act("Agendamentos excluídos") { repo.deleteAppointment(appt, andFollowing = true) }
                    }) { Text("Esta e as próximas", color = Brand.Terracotta) }
                }
            )
        } else {
            ConfirmDialog(
                title = "Excluir agendamento",
                text = "O agendamento de $clientName será apagado. Para apenas liberar o horário, use \"Desmarcou\".",
                confirmLabel = "Excluir",
                danger = true,
                onConfirm = {
                    dialog = SheetDialog.NONE
                    act("Agendamento excluído") { repo.deleteAppointment(appt, andFollowing = false) }
                },
                onDismiss = { dialog = SheetDialog.NONE }
            )
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Brand.Bronze,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint)
    }
}

/**
 * Diálogo de pagamento: valor, forma de pagamento e a opção de receber depois.
 * `onConfirm` recebe 0 quando o pagamento fica pendente.
 */
@Composable
fun PaymentDialog(
    title: String,
    suggestedCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, PayMethod) -> Unit,
    allowLater: Boolean = true,
    confirmLabel: String = "Concluir"
) {
    var receiveNow by remember { mutableStateOf(true) }
    var amount by remember { mutableLongStateOf(suggestedCents) }
    var method by remember { mutableStateOf(PayMethod.PIX) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (allowLater) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recebi o pagamento agora", modifier = Modifier.weight(1f))
                        Switch(checked = receiveNow, onCheckedChange = { receiveNow = it })
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (receiveNow) {
                    MoneyField(cents = amount, onChange = { amount = it }, label = "Valor recebido", modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    PayMethodChips(selected = method, onSelect = { method = it })
                } else {
                    Text(
                        "O valor fica em \"A receber\" no Caixa até o pagamento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (receiveNow) amount else 0L, method) }) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Concluir atendimento: pagamento (quando não é sessão de pacote) e anotação de evolução. */
@Composable
private fun ConcludeDialog(
    usesPackage: Boolean,
    suggestedCents: Long,
    initialEvolution: String,
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long, method: PayMethod, evolution: String) -> Unit
) {
    var receiveNow by remember { mutableStateOf(true) }
    var amount by remember { mutableLongStateOf(suggestedCents) }
    var method by remember { mutableStateOf(PayMethod.PIX) }
    var evolution by remember { mutableStateOf(initialEvolution) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Concluir atendimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (usesPackage) {
                    Text(
                        "Sessão do pacote: nada a receber agora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recebi o pagamento agora", modifier = Modifier.weight(1f))
                        Switch(checked = receiveNow, onCheckedChange = { receiveNow = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (receiveNow) {
                        MoneyField(cents = amount, onChange = { amount = it }, label = "Valor recebido", modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        PayMethodChips(selected = method, onSelect = { method = it })
                    } else {
                        Text(
                            "O valor fica em \"A receber\" no Caixa até o pagamento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.BrownSoft
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = evolution,
                    onValueChange = { evolution = it },
                    label = { Text("Evolução da sessão (opcional)") },
                    placeholder = { Text("O que foi feito e como a cliente respondeu") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (!usesPackage && receiveNow) amount else 0L, method, evolution) }) {
                Text("Concluir", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Anotar ou alterar a evolução de uma sessão. */
@Composable
fun EvolutionDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Evolução da sessão") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("O que foi feito, regiões trabalhadas, como a cliente respondeu, orientações") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Salvar", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
