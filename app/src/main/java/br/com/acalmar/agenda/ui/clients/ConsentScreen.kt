package br.com.acalmar.agenda.ui.clients

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.data.Consent
import br.com.acalmar.agenda.data.ConsentModel
import br.com.acalmar.agenda.domain.decodeSignature
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateLong
import br.com.acalmar.agenda.domain.sortKey
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SignaturePad
import br.com.acalmar.agenda.ui.components.SignatureState
import br.com.acalmar.agenda.ui.components.SignatureView
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.components.todayEpochDay
import br.com.acalmar.agenda.ui.render.newShareFile
import br.com.acalmar.agenda.ui.render.renderPdfPages
import br.com.acalmar.agenda.ui.render.shareFile
import br.com.acalmar.agenda.ui.render.writeConsentPdf
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Termo de consentimento da cliente: mostra o termo já assinado ou abre a tela para a cliente
 * ler, escolher sobre o uso de imagem e assinar com o dedo.
 */
@Composable
fun ConsentScreen(clientId: Long, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    val client by remember(clientId) { repo.client(clientId) }.collectAsStateWithLifecycle(initialValue = null)
    val consents by remember(clientId) { repo.consents(clientId) }.collectAsStateWithLifecycle(initialValue = null)
    val business by repo.business.collectAsStateWithLifecycle(initialValue = BusinessInfo())
    val template by repo.consentText.collectAsStateWithLifecycle(initialValue = ConsentModel.DEFAULT_TEXT)

    var signingNew by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Consent?>(null) }
    var previewing by remember { mutableStateOf<Consent?>(null) }

    val list = consents
    val latest = list?.firstOrNull()
    val signing = list != null && (latest == null || signingNew)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = "Termo de consentimento",
                onBack = { if (signingNew && latest != null) signingNew = false else onBack() },
                actions = {
                    if (!signing && latest != null) {
                        IconButton(onClick = { confirmDelete = latest }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Apagar termo", tint = Brand.Terracotta)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val name = client?.name.orEmpty()
        when {
            list == null -> Unit

            signing -> SignForm(
                text = ConsentModel.fill(template, name, business),
                clientName = name,
                modifier = Modifier.padding(padding),
                onSave = { imageUse, signature, text ->
                    scope.launch {
                        repo.signConsent(
                            Consent(
                                clientId = clientId,
                                text = text,
                                imageUse = imageUse,
                                signedOn = todayEpochDay(),
                                signerName = name,
                                signature = signature
                            )
                        )
                        signingNew = false
                        notifier.show("Termo assinado e guardado na ficha")
                    }
                }
            )

            latest != null -> SignedConsent(
                consent = latest,
                olderCount = (list?.size ?: 1) - 1,
                modifier = Modifier.padding(padding),
                onPreview = { previewing = latest },
                onSignNew = { signingNew = true }
            )
        }
    }

    confirmDelete?.let { consent ->
        ConfirmDialog(
            title = "Apagar este termo?",
            text = "O termo assinado em ${formatDateLong(epochToDate(consent.signedOn))} será apagado da ficha.",
            confirmLabel = "Apagar",
            danger = true,
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    repo.deleteConsent(consent.id)
                    notifier.show("Termo apagado")
                }
            },
            onDismiss = { confirmDelete = null }
        )
    }

    previewing?.let { consent ->
        ConsentPdfDialog(consent = consent, business = business, onDismiss = { previewing = null })
    }
}

/** Tela entregue à cliente: texto do termo, escolha sobre uso de imagem e assinatura. */
@Composable
private fun SignForm(
    text: String,
    clientName: String,
    modifier: Modifier = Modifier,
    onSave: (imageUse: Boolean, signature: String, text: String) -> Unit
) {
    val signature = remember { SignatureState() }
    var imageUse by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // O texto rola dentro do próprio quadro; a assinatura fica sempre visível embaixo.
        SoftCard(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ConsentModel.IMAGE_QUESTION,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = imageUse, onCheckedChange = { imageUse = it })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Assinatura de ${clientName.ifBlank { "cliente" }}", Modifier.weight(1f))
            TextButton(onClick = { signature.clear() }, enabled = !signature.isEmpty) { Text("Limpar") }
        }
        SignaturePad(
            state = signature,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        )
        PrimaryButton(
            text = "Salvar termo assinado",
            enabled = !signature.isEmpty,
            onClick = { onSave(imageUse, signature.encode(), text) },
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun SignedConsent(
    consent: Consent,
    olderCount: Int,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit,
    onSignNew: () -> Unit
) {
    val signature = remember(consent.signature) { decodeSignature(consent.signature) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SoftCard(Modifier.fillMaxWidth(), container = Brand.SageSoft, border = Brand.SageSoft) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Assinado em ${formatDateLong(epochToDate(consent.signedOn))}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.Sage
                )
                Spacer(Modifier.height(8.dp))
                if (consent.imageUse) {
                    Tag("Uso de imagem autorizado", Brand.Card, Brand.Sage)
                } else {
                    Tag("Uso de imagem não autorizado", Brand.Card, Brand.Terracotta)
                }
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Text(consent.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
        }

        SectionLabel("Assinatura")
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.White, MaterialTheme.shapes.medium)
                .border(1.dp, Brand.Line, MaterialTheme.shapes.medium)
                .padding(8.dp)
        ) {
            SignatureView(signature, Modifier.fillMaxSize())
        }
        if (consent.signerName.isNotBlank()) {
            Text(consent.signerName, style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft)
        }

        Button(
            onClick = onPreview,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ver e enviar o PDF", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = onSignNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Assinar um novo termo")
        }
        if (olderCount > 0) {
            Text(
                if (olderCount == 1) "Há mais 1 termo anterior guardado nesta ficha." else "Há mais $olderCount termos anteriores guardados nesta ficha.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Mostra o PDF do termo em tela cheia, com o botão de enviar. */
@Composable
private fun ConsentPdfDialog(consent: Consent, business: BusinessInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current

    // O PDF é gerado uma vez e as páginas viram imagens para aparecer na tela.
    val result by produceState<Result<Pair<File, List<Bitmap>>>?>(initialValue = null, consent.id, business) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val who = sortKey(consent.signerName).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "cliente" }
                val file = newShareFile(context, "termo-$who.pdf")
                writeConsentPdf(context, consent, business, file)
                file to renderPdfPages(file, 1200)
            }
        }
    }
    val loaded = result?.getOrNull()
    val failed = result?.isFailure == true

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Brand.GraySoft) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brand.Cream)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Fechar")
                    }
                    Text("Termo em PDF", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = loaded != null,
                        onClick = {
                            val file = loaded?.first
                            if (file == null || !shareFile(context, file, "application/pdf", "Enviar termo")) {
                                notifier.show("Não foi possível enviar o PDF")
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (failed) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Não foi possível gerar o PDF.", color = Brand.Terracotta)
                    }
                } else if (loaded == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Brand.Bronze)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(loaded.second) { page ->
                            Image(
                                bitmap = page.asImageBitmap(),
                                contentDescription = "Página do termo",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
