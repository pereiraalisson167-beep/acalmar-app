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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import br.com.acalmar.agenda.domain.defaultPaymentDay
import br.com.acalmar.agenda.ui.components.paymentDayLabel
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

/** Como o pacote novo foi (ou será) pago. */
private enum class PayMode(val label: String) {
    NOW("Recebi agora"),
    LATER("Recebe depois"),
    BEFORE("Já foi pago antes")
}

/**
 * Venda de um pacote de sessões para a cliente. Com `packageId`, corrige um pacote já vendido
 * (sessões, valor, datas) sem lançar nada de novo no caixa.
 */
@Composable
fun SellPackageScreen(clientId: Long, packageId: Long = 0L, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val editing = packageId != 0L
    val today = todayEpochDay()

    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val offers by repo.offers.collectAsStateWithLifecycle(initialValue = emptyList())
    val packageStates by repo.packageStates.collectAsStateWithLifecycle(initialValue = emptyList())

    var original by remember { mutableStateOf<ClientPackage?>(null) }
    var loaded by remember { mutableStateOf(!editing) }
    var filled by rememberSaveable { mutableStateOf(false) }
    var serviceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var serviceName by rememberSaveable { mutableStateOf("") }
    var sessions by rememberSaveable { mutableStateOf<Int?>(5) }
    var priceCents by rememberSaveable { mutableLongStateOf(0L) }
    var validityDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var usedBefore by rememberSaveable { mutableStateOf<Int?>(null) }
    var purchasedOn by rememberSaveable { mutableLongStateOf(today) }
    var payMode by rememberSaveable { mutableStateOf(PayMode.NOW) }
    var paidCents by rememberSaveable { mutableLongStateOf(0L) }
    var method by rememberSaveable { mutableStateOf(PayMethod.PIX) }
    // Dia em que o pagamento entra no caixa; nulo = acompanha a data da compra.
    var payDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var paidBeforeCents by rememberSaveable { mutableLongStateOf(0L) }
    var discountCents by rememberSaveable { mutableLongStateOf(0L) }
    var pickingService by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingPayDay by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(packageId) {
        if (editing) {
            repo.getClientPackage(packageId)?.let { p ->
                original = p
                if (!filled) {
                    serviceId = p.serviceId
                    serviceName = p.serviceName
                    sessions = p.totalSessions
                    priceCents = p.priceCents
                    validityDays = p.expiresOn?.let { (it - p.purchasedOn).toInt() }
                    usedBefore = p.usedBefore.takeIf { it > 0 }
                    purchasedOn = p.purchasedOn
                    paidBeforeCents = p.paidBeforeCents
                    discountCents = p.discountCents
                }
            }
            loaded = true
        }
        filled = true
    }

    val state = packageStates.firstOrNull { it.pkg.id == packageId }
    // Sessões feitas dentro do app (sem contar as informadas como "já feitas").
    val usedInApp = if (state != null) state.used - state.pkg.usedBefore else 0
    val cashPaid = state?.paidCents ?: 0L
    val serviceOffers = offers.filter { it.serviceId == serviceId }
    val total = sessions ?: 0
    val minSessions = usedInApp + (usedBefore ?: 0)
    val sessionsOk = total > 0 && (usedBefore ?: 0) < total && total >= minSessions
    val canSave = loaded && serviceName.isNotBlank() && sessionsOk
    val effectivePayDay = payDay ?: defaultPaymentDay(purchasedOn, today)

    fun applyOffer(offer: PackageOffer) {
        sessions = offer.sessions
        priceCents = offer.priceCents
        paidCents = offer.priceCents
        paidBeforeCents = offer.priceCents
        validityDays = offer.validityDays
    }

    fun save() {
        scope.launch {
            val expiresOn = validityDays?.takeIf { it > 0 }?.let { purchasedOn + it }
            val current = original
            if (current != null) {
                repo.updateClientPackage(
                    current.copy(
                        serviceId = serviceId,
                        serviceName = serviceName,
                        totalSessions = total,
                        priceCents = priceCents,
                        purchasedOn = purchasedOn,
                        expiresOn = expiresOn,
                        usedBefore = usedBefore ?: 0,
                        paidBeforeCents = paidBeforeCents,
                        discountCents = discountCents
                    )
                )
                notifier.show("Pacote corrigido")
            } else {
                val payment = if (payMode == PayMode.NOW && paidCents > 0) {
                    PaymentInput(paidCents, method, effectivePayDay)
                } else {
                    null
                }
                repo.sellPackage(
                    ClientPackage(
                        clientId = clientId,
                        serviceId = serviceId,
                        serviceName = serviceName,
                        totalSessions = total,
                        priceCents = priceCents,
                        purchasedOn = purchasedOn,
                        expiresOn = expiresOn,
                        usedBefore = usedBefore ?: 0,
                        paidBeforeCents = if (payMode == PayMode.BEFORE) paidBeforeCents else 0L
                    ),
                    payment
                )
                notifier.show("Pacote registrado")
            }
            onDone()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar(if (editing) "Corrigir pacote" else "Vender pacote", onBack = onDone) },
        bottomBar = {
            PrimaryButton(
                text = if (editing) "Salvar correção" else "Salvar pacote",
                enabled = canSave,
                onClick = { save() },
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
                value = serviceName.ifBlank { "Escolher serviço" },
                placeholder = serviceName.isBlank(),
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
                        if (!editing) {
                            paidCents = it
                            paidBeforeCents = it
                        }
                    },
                    label = "Valor do pacote",
                    modifier = Modifier.weight(1.4f)
                )
            }
            if (editing && total in 1 until minSessions) {
                Text(
                    "Este pacote já tem $minSessions ${if (minSessions == 1) "sessão feita" else "sessões feitas"}: o total não pode ser menor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Terracotta
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
                    label = "Validade em dias",
                    modifier = Modifier.weight(1f)
                )
                IntField(
                    value = usedBefore,
                    onChange = { usedBefore = it },
                    label = "Sessões já feitas",
                    modifier = Modifier.weight(1f),
                    maxDigits = 2
                )
            }
            Text(
                "Validade em branco = pacote sem prazo. \"Sessões já feitas\" serve para pacotes que começaram antes do app.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )

            SectionLabel("Pagamento", Modifier.padding(top = 4.dp))
            if (editing) {
                // Corrigir não mexe no caixa: os pagamentos já lançados continuam como estão.
                Text(
                    "Recebido pelo caixa: ${formatMoney(cashPaid)}. Para mudar esse valor, altere o lançamento no Caixa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
                MoneyField(
                    cents = paidBeforeCents,
                    onChange = { paidBeforeCents = it },
                    label = "Já pago antes do app",
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(
                    cents = discountCents,
                    onChange = { discountCents = it },
                    label = "Desconto",
                    modifier = Modifier.fillMaxWidth()
                )
                val due = (priceCents - cashPaid - paidBeforeCents - discountCents).coerceAtLeast(0)
                Text(
                    if (due > 0) "Falta receber ${formatMoney(due)}." else "Pacote quitado.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (due > 0) Brand.Terracotta else Brand.Sage
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayMode.entries.forEach { mode ->
                        ChoiceChip(mode.label, selected = payMode == mode, onClick = { payMode = mode })
                    }
                }
                when (payMode) {
                    PayMode.NOW -> {
                        MoneyField(cents = paidCents, onChange = { paidCents = it }, label = "Valor recebido", modifier = Modifier.fillMaxWidth())
                        PayMethodChips(selected = method, onSelect = { method = it })
                        FieldRow(
                            label = "Entra no caixa de",
                            value = paymentDayLabel(effectivePayDay, today),
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = { pickingPayDay = true }
                        )
                        if (paidCents < priceCents) {
                            Text(
                                "O restante (${formatMoney(priceCents - paidCents)}) fica em \"A receber\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.BrownSoft
                            )
                        }
                    }

                    PayMode.LATER -> Text(
                        "O valor do pacote fica em \"A receber\" no Caixa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )

                    PayMode.BEFORE -> {
                        MoneyField(
                            cents = paidBeforeCents,
                            onChange = { paidBeforeCents = it },
                            label = "Quanto já foi pago",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Para pacotes comprados antes de usar o app: esse valor não entra no caixa nem vira dívida." +
                                if (paidBeforeCents < priceCents) {
                                    " O restante (${formatMoney(priceCents - paidBeforeCents)}) fica em \"A receber\"."
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.BrownSoft
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickingService) {
        PickerDialog(
            title = "Serviço",
            options = services.filter { it.active || it.id == serviceId },
            label = { it.name },
            sublabel = { s ->
                offers.filter { it.serviceId == s.id }
                    .joinToString(" · ") { "${it.sessions} sessões por ${formatMoney(it.priceCents)}" }
                    .ifBlank { null }
            },
            selected = services.firstOrNull { it.id == serviceId },
            onPick = { s ->
                serviceId = s.id
                serviceName = s.name
                pickingService = false
                if (!editing) offers.firstOrNull { it.serviceId == s.id }?.let { applyOffer(it) }
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
    if (pickingPayDay) {
        DayPickerDialog(
            initialDay = effectivePayDay,
            onPick = {
                payDay = it
                pickingPayDay = false
            },
            onDismiss = { pickingPayDay = false }
        )
    }
}
