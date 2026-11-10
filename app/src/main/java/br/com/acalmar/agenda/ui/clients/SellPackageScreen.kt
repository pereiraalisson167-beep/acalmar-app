package br.com.acalmar.agenda.ui.clients

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.ClientPackage
import br.com.acalmar.agenda.data.PackageOffer
import br.com.acalmar.agenda.data.PayMethod
import br.com.acalmar.agenda.data.PaymentInput
import br.com.acalmar.agenda.data.Service
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateLong
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.DayPickerDialog
import br.com.acalmar.agenda.ui.components.FieldRow
import br.com.acalmar.agenda.ui.components.IntField
import br.com.acalmar.agenda.ui.components.MoneyField
import br.com.acalmar.agenda.ui.components.PayMethodChips
import br.com.acalmar.agenda.ui.components.PickerDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

/** Venda de um pacote de sessões para a cliente. */
@Composable
fun SellPackageScreen(clientId: Long, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val offers by repo.offers.collectAsStateWithLifecycle(initialValue = emptyList())

    var service by remember { mutableStateOf<Service?>(null) }
    var sessions by remember { mutableStateOf<Int?>(5) }
    var priceCents by remember { mutableLongStateOf(0L) }
    var validityDays by remember { mutableStateOf<Int?>(null) }
    var usedBefore by remember { mutableStateOf<Int?>(null) }
    var purchasedOn by remember { mutableLongStateOf(todayEpochDay()) }
    var receiveNow by remember { mutableStateOf(true) }
    var paidCents by remember { mutableLongStateOf(0L) }
    var method by remember { mutableStateOf(PayMethod.PIX) }
    var pickingService by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    val serviceOffers = offers.filter { it.serviceId == service?.id }
    val total = sessions ?: 0
    val canSave = service != null && total > 0 && (usedBefore ?: 0) < total

    fun applyOffer(offer: PackageOffer) {
        sessions = offer.sessions
        priceCents = offer.priceCents
        paidCents = offer.priceCents
        validityDays = offer.validityDays
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Vender pacote", onBack = onDone) },
        bottomBar = {
            PrimaryButton(
                text = "Salvar pacote",
                enabled = canSave,
                onClick = {
                    val chosen = service ?: return@PrimaryButton
                    scope.launch {
                        val payment = if (receiveNow && paidCents > 0) PaymentInput(paidCents, method, todayEpochDay()) else null
                        repo.sellPackage(
                            ClientPackage(
                                clientId = clientId,
                                serviceId = chosen.id,
                                serviceName = chosen.name,
                                totalSessions = total,
                                priceCents = priceCents,
                                purchasedOn = purchasedOn,
                                expiresOn = validityDays?.takeIf { it > 0 }?.let { purchasedOn + it },
                                usedBefore = usedBefore ?: 0
                            ),
                            payment
                        )
                        notifier.show("Pacote registrado")
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
            FieldRow(
                label = "Serviço",
                value = service?.name ?: "Escolher serviço",
                placeholder = service == null,
                icon = Icons.Outlined.Spa,
                onClick = { pickingService = true }
            )
            if (serviceOffers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    serviceOffers.forEach { offer ->
                        ChoiceChip(
                            "${offer.sessions} sessões · ${formatMoney(offer.priceCents)}",
                            selected = sessions == offer.sessions && priceCents == offer.priceCents,
                            onClick = { applyOffer(offer) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntField(value = sessions, onChange = { sessions = it }, label = "Sessões", modifier = Modifier.weight(1f), maxDigits = 2)
                MoneyField(
                    cents = priceCents,
                    onChange = {
                        priceCents = it
                        paidCents = it
                    },
                    label = "Valor do pacote",
                    modifier = Modifier.weight(1.4f)
                )
            }
            FieldRow(
                label = "Data da compra",
                value = formatDateLong(epochToDate(purchasedOn)),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { pickingDate = true }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntField(
                    value = validityDays,
                    onChange = { validityDays = it },
                    label = "Validade",
                    suffix = "dias",
                    modifier = Modifier.weight(1f)
                )
                IntField(
                    value = usedBefore,
                    onChange = { usedBefore = it },
                    label = "Já feitas",
                    suffix = "sessões",
                    modifier = Modifier.weight(1f),
                    maxDigits = 2
                )
            }
            Text(
                "Validade em branco = pacote sem prazo. \"Já feitas\" serve para pacotes que começaram antes do app.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )

            SectionLabel("Pagamento", Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recebi o pagamento agora", modifier = Modifier.weight(1f))
                Switch(checked = receiveNow, onCheckedChange = { receiveNow = it })
            }
            if (receiveNow) {
                MoneyField(cents = paidCents, onChange = { paidCents = it }, label = "Valor recebido", modifier = Modifier.fillMaxWidth())
                PayMethodChips(selected = method, onSelect = { method = it })
                if (paidCents < priceCents) {
                    Text(
                        "O restante (${formatMoney(priceCents - paidCents)}) fica em \"A receber\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )
                }
            } else {
                Text(
                    "O valor do pacote fica em \"A receber\" no Caixa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickingService) {
        PickerDialog(
            title = "Serviço",
            options = services.filter { it.active },
            label = { it.name },
            sublabel = { s ->
                offers.filter { it.serviceId == s.id }
                    .joinToString(" · ") { "${it.sessions} sessões por ${formatMoney(it.priceCents)}" }
                    .ifBlank { null }
            },
            selected = service,
            onPick = { s ->
                service = s
                pickingService = false
                offers.firstOrNull { it.serviceId == s.id }?.let { applyOffer(it) }
            },
            onDismiss = { pickingService = false }
        )
    }
    if (pickingDate) {
        DayPickerDialog(
            initialDay = purchasedOn,
            onPick = {
                purchasedOn = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false }
        )
    }
}
