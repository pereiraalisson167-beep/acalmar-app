package br.com.acalmar.agenda.ui.clients

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.data.Photo
import br.com.acalmar.agenda.data.PhotoKind
import br.com.acalmar.agenda.domain.epochToDate
import br.com.acalmar.agenda.domain.formatDateLong
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.domain.friendlyDate
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.DayPickerDialog
import br.com.acalmar.agenda.ui.components.EmptyState
import br.com.acalmar.agenda.ui.components.FieldRow
import br.com.acalmar.agenda.ui.components.PhotoImage
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.components.decodeSampled
import br.com.acalmar.agenda.ui.render.renderComparison
import br.com.acalmar.agenda.ui.render.saveBitmapForShare
import br.com.acalmar.agenda.ui.render.shareFile
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Fotos da cliente: antes, depois e outras. Ficam guardadas só dentro do app. */
@Composable
fun PhotosScreen(clientId: Long, onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()

    val client by remember(clientId) { repo.client(clientId) }.collectAsStateWithLifecycle(initialValue = null)
    val photos by remember(clientId) { repo.photos(clientId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val consents by remember(clientId) { repo.consents(clientId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val business by repo.business.collectAsStateWithLifecycle(initialValue = BusinessInfo())

    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var viewingId by remember { mutableStateOf<Long?>(null) }
    var comparing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingUri = uri.toString()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) pendingUri = cameraUri
    }

    val grouped = remember(photos) { photos.groupBy { it.takenOn }.toList().sortedByDescending { it.first } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = "Fotos",
                onBack = onBack,
                actions = {
                    if (photos.size >= 2) {
                        TextButton(onClick = { comparing = true }) { Text("Antes e depois", fontWeight = FontWeight.SemiBold) }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(client?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (photos.size) {
                        0 -> "Nenhuma foto guardada"
                        1 -> "1 foto guardada"
                        else -> "${photos.size} fotos guardadas"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val result = runCatching {
                                val uri = repo.photoStore.newCameraUri()
                                cameraUri = uri.toString()
                                camera.launch(uri)
                            }
                            if (result.isFailure) notifier.show("Não foi possível abrir a câmera")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tirar foto")
                    }
                    OutlinedButton(
                        onClick = {
                            val result = runCatching {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            if (result.isFailure) notifier.show("Não foi possível abrir a galeria")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Da galeria")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (photos.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Registre o antes e o depois",
                    subtitle = "As fotos ficam guardadas só dentro do app e não aparecem na galeria do celular."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { (day, list) ->
                        item(key = "d$day", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                friendlyDate(epochToDate(day), today),
                                style = MaterialTheme.typography.labelLarge,
                                color = Brand.Bronze,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(list, key = { it.id }) { photo ->
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { viewingId = photo.id }
                            ) {
                                PhotoImage(
                                    file = repo.photoStore.file(photo.fileName),
                                    maxEdge = 400,
                                    modifier = Modifier.fillMaxSize(),
                                    contentDescription = "Foto: ${photo.kind.label}"
                                )
                                if (photo.kind != PhotoKind.OUTRA) {
                                    Tag(
                                        photo.kind.label,
                                        Brand.Brown.copy(alpha = 0.75f),
                                        Color.White,
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Foto escolhida: falta dizer se é antes, depois ou outra
    val pending = pendingUri
    if (pending != null) {
        NewPhotoDialog(
            suggestedKind = if (photos.any { it.takenOn == todayDay && it.kind == PhotoKind.ANTES }) PhotoKind.DEPOIS else PhotoKind.ANTES,
            today = todayDay,
            saving = saving,
            onDismiss = { if (!saving) pendingUri = null },
            onSave = { kind, takenOn, note ->
                saving = true
                scope.launch {
                    val result = runCatching { repo.addPhoto(clientId, Uri.parse(pending), kind, takenOn, note) }
                    saving = false
                    pendingUri = null
                    notifier.show(if (result.isSuccess) "Foto guardada" else "Não foi possível guardar a foto")
                }
            }
        )
    }

    // ---- Foto aberta em tela cheia
    val viewing = viewingId?.let { id -> photos.firstOrNull { it.id == id } }
    if (viewing != null) {
        PhotoViewer(
            photo = viewing,
            file = repo.photoStore.file(viewing.fileName),
            onDismiss = { viewingId = null },
            onChangeKind = { kind -> scope.launch { repo.updatePhoto(viewing.copy(kind = kind)) } },
            onDelete = {
                viewingId = null
                scope.launch {
                    repo.deletePhoto(viewing)
                    notifier.show("Foto apagada")
                }
            }
        )
    }

    if (comparing && photos.size >= 2) {
        CompareDialog(
            photos = photos,
            fileOf = { repo.photoStore.file(it.fileName) },
            business = business,
            imageUseAllowed = consents.firstOrNull()?.imageUse == true,
            onDismiss = { comparing = false }
        )
    }
}

@Composable
private fun NewPhotoDialog(
    suggestedKind: PhotoKind,
    today: Long,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (PhotoKind, Long, String) -> Unit
) {
    var kind by remember { mutableStateOf(suggestedKind) }
    var takenOn by remember { mutableLongStateOf(today) }
    var note by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoKind.entries.forEach { option ->
                        ChoiceChip(option.label, selected = kind == option, onClick = { kind = option })
                    }
                }
                FieldRow(
                    label = "Data da foto",
                    value = formatDateLong(epochToDate(takenOn)),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { pickingDate = true }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Observação (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                if (saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Brand.Bronze)
                        Spacer(Modifier.width(10.dp))
                        Text("Guardando a foto…", color = Brand.BrownSoft)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onSave(kind, takenOn, note) }) {
                Text("Guardar foto", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } }
    )

    if (pickingDate) {
        DayPickerDialog(
            initialDay = takenOn,
            onPick = {
                takenOn = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false }
        )
    }
}

/** Foto em tela cheia: dá para ampliar com dois dedos, trocar a etiqueta, enviar ou apagar. */
@Composable
private fun PhotoViewer(
    photo: Photo,
    file: File,
    onDismiss: () -> Unit,
    onChangeKind: (PhotoKind) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    var confirmDelete by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + panChange
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                PhotoImage(
                    file = file,
                    maxEdge = 1600,
                    contentScale = ContentScale.Fit,
                    contentDescription = "Foto ampliada",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                        .transformable(transform)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        if (!shareFile(context, file, "image/jpeg", "Enviar foto")) notifier.show("Não foi possível enviar a foto")
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Enviar foto", tint = Color.White)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Apagar foto", tint = Color.White)
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        formatDateLong(epochToDate(photo.takenOn)) + if (photo.note.isBlank()) "" else " · ${photo.note}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoKind.entries.forEach { option ->
                            ChoiceChip(option.label, selected = photo.kind == option, onClick = { onChangeKind(option) })
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Apagar esta foto?",
            text = "A foto será apagada da ficha da cliente. Esta ação não pode ser desfeita.",
            confirmLabel = "Apagar",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Duas fotos lado a lado. Tocar em um lado troca a foto; "Enviar" gera uma imagem única. */
@Composable
private fun CompareDialog(
    photos: List<Photo>,
    fileOf: (Photo) -> File,
    business: BusinessInfo,
    imageUseAllowed: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    // Por padrão: o "antes" mais antigo e o "depois" mais recente.
    val oldestFirst = remember(photos) { photos.sortedWith(compareBy<Photo>({ it.takenOn }, { it.id })) }
    val defaultBefore = oldestFirst.firstOrNull { it.kind == PhotoKind.ANTES } ?: oldestFirst.first()
    val defaultAfter = oldestFirst.lastOrNull { it.kind == PhotoKind.DEPOIS && it.id != defaultBefore.id }
        ?: oldestFirst.last { it.id != defaultBefore.id }
    var beforeId by remember { mutableLongStateOf(defaultBefore.id) }
    var afterId by remember { mutableLongStateOf(defaultAfter.id) }
    var pickingSide by remember { mutableStateOf<Int?>(null) }
    var sharing by remember { mutableStateOf(false) }

    val before = photos.firstOrNull { it.id == beforeId } ?: defaultBefore
    val after = photos.firstOrNull { it.id == afterId } ?: defaultAfter

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Brand.Cream) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Fechar")
                    }
                    Text("Antes e depois", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = !sharing,
                        onClick = {
                            sharing = true
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val left = decodeSampled(fileOf(before), 1600) ?: error("foto ausente")
                                        val right = decodeSampled(fileOf(after), 1600) ?: error("foto ausente")
                                        val image = renderComparison(
                                            context, left, right,
                                            formatDateShort(epochToDate(before.takenOn)),
                                            formatDateShort(epochToDate(after.takenOn)),
                                            business
                                        )
                                        saveBitmapForShare(context, image, "antes-e-depois.png")
                                    }.getOrNull()
                                }
                                sharing = false
                                if (file == null || !shareFile(context, file, "image/png", "Enviar antes e depois")) {
                                    notifier.show("Não foi possível gerar a imagem")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar", fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComparePanel("ANTES", before, fileOf(before), Modifier.weight(1f)) { pickingSide = 0 }
                    ComparePanel("DEPOIS", after, fileOf(after), Modifier.weight(1f)) { pickingSide = 1 }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Toque em uma das fotos para trocar. \"Enviar\" gera uma imagem única com as duas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft
                    )
                    if (!imageUseAllowed) {
                        Text(
                            "Antes de divulgar: esta cliente ainda não autorizou o uso de imagem no termo de consentimento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.Terracotta
                        )
                    }
                    if (sharing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Brand.Bronze)
                            Spacer(Modifier.width(10.dp))
                            Text("Gerando a imagem…", color = Brand.BrownSoft)
                        }
                    }
                }
            }
        }
    }

    val side = pickingSide
    if (side != null) {
        AlertDialog(
            onDismissRequest = { pickingSide = null },
            title = { Text(if (side == 0) "Foto do antes" else "Foto do depois") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    if (side == 0) beforeId = photo.id else afterId = photo.id
                                    pickingSide = null
                                }
                        ) {
                            PhotoImage(fileOf(photo), 300, Modifier.fillMaxSize())
                            Tag(
                                formatDateShort(epochToDate(photo.takenOn)).take(5),
                                Brand.Brown.copy(alpha = 0.75f),
                                Color.White,
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingSide = null }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun ComparePanel(label: String, photo: Photo, file: File, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        PhotoImage(file, 1000, Modifier.fillMaxSize(), contentDescription = "Foto do ${label.lowercase()}")
        Tag(
            "$label · ${formatDateShort(epochToDate(photo.takenOn))}",
            Brand.Brown.copy(alpha = 0.75f),
            Color.White,
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}
