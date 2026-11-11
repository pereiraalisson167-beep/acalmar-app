package br.com.acalmar.agenda.ui.quotes

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.data.Quote
import br.com.acalmar.agenda.data.QuoteItem
import br.com.acalmar.agenda.data.QuoteModel
import br.com.acalmar.agenda.data.QuoteStatus
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.formatMoney
import br.com.acalmar.agenda.domain.quoteItemLabel
import br.com.acalmar.agenda.domain.quoteNumber
import br.com.acalmar.agenda.domain.quoteSubtotal
import br.com.acalmar.agenda.domain.quoteText
import br.com.acalmar.agenda.domain.quoteTotal
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.clients.ClientPickerDialog
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.EmptyState
import br.com.acalmar.agenda.ui.components.FieldRow
import br.com.acalmar.agenda.ui.components.IntField
import br.com.acalmar.agenda.ui.components.LabeledValue
import br.com.acalmar.agenda.ui.components.MoneyField
import br.com.acalmar.agenda.ui.components.PickerDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.components.openWhatsApp
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.render.renderQuote
import br.com.acalmar.agenda.ui.render.saveBitmapForShare
import br.com.acalmar.agenda.ui.render.shareFile
import br.com.acalmar.agenda.ui.render.shareText
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class QuoteBadge(val label: String, val background: Color, val content: Color)

/** Situação do orçamento para mostrar na lista ("Vencido" quando passou da validade em aberto). */
private fun quoteBadge(quote: Quote, today: Long): QuoteBadge = when {
    quote.status == QuoteStatus.APROVADO -> QuoteBadge("Aprovado", Brand.SageSoft, Brand.Sage)
    quote.status == QuoteStatus.RECUSADO -> QuoteBadge("Recusado", Brand.GraySoft, Brand.Gray)
    quote.validUntil != null && quote.validUntil < today -> QuoteBadge("Vencido", Brand.TerracottaSoft, Brand.Terracotta)
    else -> QuoteBadge("Em aberto", Brand.Beige, Brand.BronzeDark)
}

// ---------------------------------------------------------------------------------------------
// Lista
// ---------------------------------------------------------------------------------------------

/** Lista de orçamentos. Com `clientId` diferente de 0, mostra só os daquela cliente. */
@Composable
fun QuotesScreen(clientId: Long, onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val repo = LocalRepo.current
    val all by repo.quotes.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = todayEpochDay()
    val quotes = remember(all, clientId) { if (clientId != 0L) all.filter { it.clientId == clientId } else all }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Orçamentos", onBack = onBack) }
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
                if (quotes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.RequestQuote,
                            title = "Nenhum orçamento ainda",
                            subtitle = "Monte o orçamento com os serviços e pacotes e envie pelo WhatsApp, já com o logo."
                        )
                    }
                }
                items(quotes, key = { it.id }) { quote ->
                    val items = remember(quote.itemsJson) { QuoteModel.parseItems(quote.itemsJson) }
                    val badge = quoteBadge(quote, today)
                    SoftCard(Modifier.fillMaxWidth(), onClick = { onOpen(quote.id) }) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    quote.clientName.ifBlank { "Sem nome" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${quoteNumber(quote.id)} · ${formatDateShort(epochToDate(quote.createdOn))}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Brand.BrownSoft
                                )
                                Spacer(Modifier.height(6.dp))
                                Tag(badge.label, badge.background, badge.content)
                            }
                            Text(
                                formatMoney(quoteTotal(items, quote.discountCents)),
                                style = MaterialTheme.typography.titleMedium,
                                color = Brand.Bronze,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = Brand.Bronze,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Novo orçamento") },
                text = { Text("Novo orçamento") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Formulário
// ---------------------------------------------------------------------------------------------

/** Opção do diálogo "Adicionar item". `item` nulo = digitar um item livre. */
private data class AddOption(val label: String, val detail: String, val item: QuoteItem?)

@Composable
fun QuoteFormScreen(quoteId: Long, initialClientId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val editing = quoteId != 0L
    val today = todayEpochDay()

    val clients by repo.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val offers by repo.offers.collectAsStateWithLifecycle(initialValue = emptyList())

    var original by remember { mutableStateOf<Quote?>(null) }
    var loaded by remember { mutableStateOf(!editing) }
    var clientId by remember { mutableStateOf<Long?>(initialClientId.takeIf { it != 0L }) }
    var clientName by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<QuoteItem>() }
    var discountCents by remember { mutableLongStateOf(0L) }
    var validityDays by remember { mutableStateOf<Int?>(7) }
    var notes by remember { mutableStateOf("") }

    var pickingClient by remember { mutableStateOf(false) }
    var addingItem by remember { mutableStateOf(false) }
    // Índice do item em edição; -1 = item novo digitado; nulo = nenhum diálogo aberto.
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(quoteId) {
        if (editing) {
            repo.getQuote(quoteId)?.let { q ->
                original = q
                clientId = q.clientId
                clientName = q.clientName
                items.clear()
                items.addAll(QuoteModel.parseItems(q.itemsJson))
                discountCents = q.discountCents
                validityDays = q.validUntil?.let { (it - q.createdOn).toInt() }
                notes = q.notes
            }
            loaded = true
        } else if (initialClientId != 0L) {
            clientName = repo.getClient(initialClientId)?.name.orEmpty()
        }
    }

    val createdOn = original?.createdOn ?: today
    val validUntil = validityDays?.takeIf { it > 0 }?.let { createdOn + it }
    val subtotal = quoteSubtotal(items)
    val total = quoteTotal(items, discountCents)
    val canSave = loaded && items.isNotEmpty() && clientName.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar(if (editing) "Editar orçamento" else "Novo orçamento", onBack = onBack) },
        bottomBar = {
            PrimaryButton(
                text = "Salvar orçamento",
                enabled = canSave,
                onClick = {
                    scope.launch {
                        val base = original ?: Quote(createdOn = createdOn)
                        val id = repo.saveQuote(
                            base.copy(
                                clientId = clientId,
                                clientName = clientName.trim(),
                                validUntil = validUntil,
                                itemsJson = QuoteModel.itemsJson(items.toList()),
                                discountCents = discountCents,
                                notes = notes.trim()
                            )
                        )
                        notifier.show("Orçamento salvo")
                        onSaved(id)
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
                label = "Cliente cadastrada (opcional)",
                value = clients.firstOrNull { it.id == clientId }?.name ?: "Escolher cliente",
                placeholder = clientId == null,
                icon = Icons.Outlined.Person,
                onClick = { pickingClient = true }
            )
            OutlinedTextField(
                value = clientName,
                onValueChange = { clientName = it },
                label = { Text("Nome no orçamento") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Itens", Modifier.padding(top = 4.dp))
            if (items.isEmpty()) {
                Text("Nenhum item. Adicione serviços ou pacotes.", style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft)
            }
            items.forEachIndexed { index, item ->
                SoftCard(Modifier.fillMaxWidth(), onClick = { editingIndex = index }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.description, style = MaterialTheme.typography.bodyLarge)
                            if (item.quantity > 1) {
                                Text(
                                    "${item.quantity} × ${formatMoney(item.unitCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.BrownSoft
                                )
                            }
                        }
                        Text(formatMoney(item.totalCents), style = MaterialTheme.typography.titleSmall, color = Brand.Bronze)
                    }
                }
            }
            OutlinedButton(onClick = { addingItem = true }, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Adicionar item", modifier = Modifier.padding(start = 6.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyField(cents = discountCents, onChange = { discountCents = it }, label = "Desconto", modifier = Modifier.weight(1.3f))
                IntField(value = validityDays, onChange = { validityDays = it }, label = "Validade em dias", modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observações (opcional)") },
                placeholder = { Text("Formas de pagamento, condições…") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SoftCard(Modifier.fillMaxWidth(), container = Brand.BeigeSoft) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (discountCents > 0) {
                        LabeledValue("Subtotal", formatMoney(subtotal))
                        LabeledValue("Desconto", "− " + formatMoney(discountCents))
                    }
                    LabeledValue("Total", formatMoney(total), valueColor = Brand.BronzeDark)
                    Text(
                        if (validUntil != null) "Válido até ${formatDateShort(epochToDate(validUntil))}" else "Sem prazo de validade",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickingClient) {
        ClientPickerDialog(
            clients = clients,
            onPick = { id ->
                clientId = id
                pickingClient = false
                scope.launch { repo.getClient(id)?.let { clientName = it.name } }
            },
            onDismiss = { pickingClient = false }
        )
    }

    if (addingItem) {
        val options = remember(services, offers) {
            val serviceNames = services.associate { it.id to it.name }
            services.filter { it.active }.map { s ->
                AddOption(s.name, "Sessão avulsa · ${formatMoney(s.priceCents)}", QuoteItem(s.name, 1, s.priceCents))
            } + offers.mapNotNull { o ->
                val name = serviceNames[o.serviceId] ?: return@mapNotNull null
                AddOption(
                    "Pacote de ${o.sessions} sessões · $name",
                    formatMoney(o.priceCents),
                    QuoteItem("Pacote de ${o.sessions} sessões · $name", 1, o.priceCents)
                )
            } + AddOption("Outro item", "Digitar a descrição e o valor", null)
        }
        PickerDialog(
            title = "Adicionar item",
            options = options,
            label = { it.label },
            sublabel = { it.detail },
            onPick = { option ->
                addingItem = false
                if (option.item != null) items.add(option.item) else editingIndex = -1
            },
            onDismiss = { addingItem = false }
        )
    }

    val index = editingIndex
    if (index != null) {
        val current = items.getOrNull(index)
        ItemDialog(
            item = current,
            onDismiss = { editingIndex = null },
            onSave = { saved ->
                if (current != null && index in items.indices) items[index] = saved else items.add(saved)
                editingIndex = null
            },
            onDelete = {
                if (index in items.indices) items.removeAt(index)
                editingIndex = null
            }
        )
    }
}

@Composable
private fun ItemDialog(item: QuoteItem?, onDismiss: () -> Unit, onSave: (QuoteItem) -> Unit, onDelete: () -> Unit) {
    var description by remember { mutableStateOf(item?.description.orEmpty()) }
    var quantity by remember { mutableStateOf<Int?>(item?.quantity ?: 1) }
    var unitCents by remember { mutableLongStateOf(item?.unitCents ?: 0L) }
    val valid = description.isNotBlank() && (quantity ?: 0) >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Novo item" else "Editar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntField(value = quantity, onChange = { quantity = it }, label = "Qtd.", modifier = Modifier.weight(0.7f), maxDigits = 2)
                    MoneyField(cents = unitCents, onChange = { unitCents = it }, label = "Valor unitário", modifier = Modifier.weight(1.5f))
                }
                Text(
                    "Total do item: ${formatMoney((quantity ?: 0).toLong() * unitCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Bronze,
                    fontWeight = FontWeight.SemiBold
                )
                if (item != null) {
                    TextButton(onClick = onDelete) { Text("Remover item", color = Brand.Terracotta) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(QuoteItem(description.trim(), quantity ?: 1, unitCents)) }
            ) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Visualização e envio
// ---------------------------------------------------------------------------------------------

/** Mostra o orçamento como a cliente vai receber e permite enviar como imagem ou texto. */
@Composable
fun QuoteViewScreen(quoteId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val quote by remember(quoteId) { repo.quote(quoteId) }.collectAsStateWithLifecycle(initialValue = null)
    val business by repo.business.collectAsStateWithLifecycle(initialValue = BusinessInfo())
    val clients by repo.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val current = quote
    val items = remember(current?.itemsJson) { QuoteModel.parseItems(current?.itemsJson) }
    val phone = clients.firstOrNull { it.id == current?.clientId }?.phone.orEmpty()

    val image by produceState<Bitmap?>(initialValue = null, current, business) {
        value = if (current == null) null else withContext(Dispatchers.Default) {
            runCatching { renderQuote(context, current, items, business) }.getOrNull()
        }
    }
    val bitmap = image

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = if (current != null) "Orçamento ${quoteNumber(current.id)}" else "Orçamento",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar orçamento", tint = Brand.Bronze)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Excluir orçamento", tint = Brand.Terracotta)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    enabled = bitmap != null && !sending,
                    onClick = {
                        val picture = bitmap ?: return@Button
                        sending = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                runCatching { saveBitmapForShare(context, picture, "orcamento-$quoteId.png") }.getOrNull()
                            }
                            sending = false
                            if (file == null || !shareFile(context, file, "image/png", "Enviar orçamento")) {
                                notifier.show("Não foi possível enviar a imagem")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar imagem", maxLines = 1)
                }
                OutlinedButton(
                    enabled = current != null,
                    onClick = {
                        val q = current ?: return@OutlinedButton
                        val text = quoteText(q, items, business)
                        val sent = if (phone.isNotBlank()) {
                            openWhatsApp(context, phone, text) || shareText(context, text, "Enviar orçamento")
                        } else {
                            shareText(context, text, "Enviar orçamento")
                        }
                        if (!sent) notifier.show("Não foi possível enviar o texto")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar texto", maxLines = 1)
                }
            }
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
            SectionLabel("Situação")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuoteStatus.entries.forEach { status ->
                    ChoiceChip(status.label, selected = current.status == status, onClick = {
                        if (current.status != status) {
                            scope.launch { repo.saveQuote(current.copy(status = status)) }
                        }
                    })
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, Brand.Line),
                color = Brand.Cream,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Orçamento para ${current.clientName}: " +
                            items.joinToString(", ") { quoteItemLabel(it) } +
                            ". Total ${formatMoney(quoteTotal(items, current.discountCents))}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Brand.Bronze)
                    }
                }
            }
            Text(
                "A cliente recebe a imagem como aparece acima. Nome e contato do rodapé são alterados em Mais > Dados do negócio.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir orçamento?",
            text = "O orçamento será apagado. Esta ação não pode ser desfeita.",
            confirmLabel = "Excluir",
            danger = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    repo.deleteQuote(quoteId)
                    notifier.show("Orçamento excluído")
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
