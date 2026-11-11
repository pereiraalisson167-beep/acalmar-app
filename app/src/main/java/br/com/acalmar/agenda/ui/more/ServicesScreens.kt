package br.com.acalmar.agenda.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.PackageOffer
import br.com.acalmar.agenda.data.Service
import br.com.acalmar.agenda.domain.formatDuration
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.IntField
import br.com.acalmar.agenda.ui.components.MoneyField
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

@Composable
fun ServicesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val repo = LocalRepo.current
    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val offers by repo.offers.collectAsStateWithLifecycle(initialValue = emptyList())
    val offersByService = remember(offers) { offers.groupBy { it.serviceId } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Serviços e pacotes", onBack = onBack) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(services, key = { it.id }) { service ->
                    SoftCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpen(service.id) },
                        container = if (service.active) Brand.Card else Brand.GraySoft
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(service.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (!service.active) Tag("Inativo", Brand.GraySoft, Brand.Gray)
                            }
                            Text(
                                "${formatDuration(service.durationMin)} · ${formatMoney(service.priceCents)}" +
                                    if (service.returnDays > 0) " · retorno em ${service.returnDays} dias" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Brand.BrownSoft
                            )
                            offersByService[service.id].orEmpty().forEach { offer ->
                                Text(
                                    "Pacote de ${offer.sessions} sessões · ${formatMoney(offer.priceCents)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Brand.Bronze
                                )
                            }
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { onOpen(0L) },
                containerColor = Brand.Bronze,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Novo serviço") },
                text = { Text("Novo serviço") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

private data class OfferDraft(val sessions: Int?, val priceCents: Long, val validityDays: Int?)

@Composable
fun ServiceFormScreen(serviceId: Long, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val editing = serviceId != 0L

    var original by remember { mutableStateOf<Service?>(null) }
    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf<Int?>(60) }
    var priceCents by remember { mutableLongStateOf(0L) }
    var active by remember { mutableStateOf(true) }
    var returnDays by remember { mutableStateOf<Int?>(30) }
    val drafts = remember { mutableStateListOf<OfferDraft>() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(serviceId) {
        if (editing) {
            repo.getService(serviceId)?.let { s ->
                original = s
                name = s.name
                duration = s.durationMin
                priceCents = s.priceCents
                active = s.active
                returnDays = s.returnDays.takeIf { it > 0 }
            }
            drafts.clear()
            drafts.addAll(repo.getOffersFor(serviceId).map { OfferDraft(it.sessions, it.priceCents, it.validityDays) })
        }
    }

    val durationOk = (duration ?: 0) in 5..600
    val draftsOk = drafts.all { (it.sessions ?: 0) >= 2 }
    val canSave = name.isNotBlank() && durationOk && draftsOk

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = if (editing) "Editar serviço" else "Novo serviço",
                onBack = onDone,
                actions = {
                    if (editing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Excluir serviço", tint = Brand.Terracotta)
                        }
                    }
                }
            )
        },
        bottomBar = {
            PrimaryButton(
                text = "Salvar",
                enabled = canSave,
                onClick = {
                    scope.launch {
                        val base = original ?: Service(name = "", sortOrder = 999)
                        repo.saveService(
                            base.copy(
                                name = name.trim(),
                                durationMin = duration ?: 60,
                                priceCents = priceCents,
                                active = active,
                                returnDays = returnDays ?: 0
                            ),
                            drafts.map {
                                PackageOffer(
                                    serviceId = base.id,
                                    sessions = it.sessions ?: 0,
                                    priceCents = it.priceCents,
                                    validityDays = it.validityDays?.takeIf { d -> d > 0 }
                                )
                            }
                        )
                        notifier.show("Serviço salvo")
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
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do serviço") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntField(
                    value = duration,
                    onChange = { duration = it },
                    label = "Duração",
                    suffix = "min",
                    modifier = Modifier.weight(1f)
                )
                MoneyField(cents = priceCents, onChange = { priceCents = it }, label = "Preço da sessão", modifier = Modifier.weight(1.4f))
            }
            if (!durationOk) {
                Text("Informe a duração em minutos.", style = MaterialTheme.typography.bodySmall, color = Brand.Terracotta)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Serviço ativo", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Serviços inativos não aparecem ao agendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )
                }
                Switch(checked = active, onCheckedChange = { active = it })
            }

            IntField(
                value = returnDays,
                onChange = { returnDays = it },
                label = "Chamar a cliente de volta após",
                suffix = "dias",
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Depois desse tempo sem novo horário, a cliente aparece em \"Hora de chamar de volta\". Em branco = não avisar.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )

            SectionLabel("Pacotes deste serviço", Modifier.padding(top = 4.dp))
            if (drafts.isEmpty()) {
                Text("Nenhum pacote cadastrado.", style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft)
            }
            drafts.forEachIndexed { index, draft ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pacote ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { drafts.removeAt(index) }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remover pacote", tint = Brand.Gray)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 8.dp)) {
                            IntField(
                                value = draft.sessions,
                                onChange = { drafts[index] = draft.copy(sessions = it) },
                                label = "Sessões",
                                modifier = Modifier.weight(1f),
                                maxDigits = 2
                            )
                            MoneyField(
                                cents = draft.priceCents,
                                onChange = { drafts[index] = draft.copy(priceCents = it) },
                                label = "Preço",
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        IntField(
                            value = draft.validityDays,
                            onChange = { drafts[index] = draft.copy(validityDays = it) },
                            label = "Validade em dias (opcional)",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    }
                }
            }
            if (!draftsOk) {
                Text("Cada pacote precisa ter pelo menos 2 sessões.", style = MaterialTheme.typography.bodySmall, color = Brand.Terracotta)
            }
            OutlinedButton(onClick = { drafts.add(OfferDraft(5, priceCents * 5, null)) }, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Adicionar pacote", modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir serviço?",
            text = "Os atendimentos já registrados continuam no histórico. Se preferir só esconder o serviço, desligue \"Serviço ativo\".",
            confirmLabel = "Excluir",
            danger = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    repo.deleteService(serviceId)
                    notifier.show("Serviço excluído")
                    onDone()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
