package br.com.acalmar.agenda.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.BackupFile
import br.com.acalmar.agenda.data.Keys
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Backup: salva todos os dados em um arquivo (por exemplo, no Google Drive) e restaura a partir dele. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = emptyMap())
    val (statusText, late) = backupStatus(settings[Keys.LAST_BACKUP])

    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingSummary by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val text = repo.exportBackup()
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Sem acesso ao arquivo")
                        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    repo.markBackupDone()
                }.isSuccess
                notifier.show(if (ok) "Backup salvo" else "Não foi possível salvar o backup")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val text = withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Sem acesso ao arquivo")
                        stream.use { it.readBytes().toString(Charsets.UTF_8) }
                    }
                    text to BackupFile.parse(text)
                }
                val loaded = result.getOrNull()
                if (loaded != null) {
                    val file = loaded.second
                    pendingSummary = "${file.clients.size} clientes, ${file.appointments.size} agendamentos e " +
                        "${file.cash.size} lançamentos de caixa"
                    pendingImport = loaded.first
                } else {
                    notifier.show("O arquivo escolhido não é um backup do Acalmar")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Backup", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoftCard(Modifier.fillMaxWidth(), container = if (late) Brand.BeigeSoft else Brand.Card) {
                Column(Modifier.padding(16.dp)) {
                    Text(statusText, style = MaterialTheme.typography.titleMedium, color = if (late) Brand.BronzeDark else Brand.Brown)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Os dados do app ficam só neste celular. O backup guarda uma cópia em arquivo: " +
                            "se o celular quebrar, for trocado ou o app for apagado, é com ele que tudo volta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft
                    )
                }
            }

            Button(
                onClick = { exportLauncher.launch("acalmar-backup-${LocalDate.now()}.json") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Fazer backup agora", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Na tela que abrir, escolha o Google Drive como local para salvar. Assim a cópia fica fora do celular. " +
                    "O ideal é fazer um backup por semana.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restaurar um backup", style = MaterialTheme.typography.titleMedium)
            }
            Row {
                Text(
                    "Restaurar substitui tudo o que está no app pelos dados do arquivo escolhido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.BrownSoft,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }

    val text = pendingImport
    if (text != null) {
        ConfirmDialog(
            title = "Restaurar este backup?",
            text = "O arquivo tem $pendingSummary. Tudo o que está no app agora será substituído por esses dados.",
            confirmLabel = "Restaurar",
            danger = true,
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val ok = runCatching { repo.importBackup(text) }.isSuccess
                    notifier.show(if (ok) "Backup restaurado" else "Não foi possível restaurar o backup")
                }
            },
            onDismiss = { pendingImport = null }
        )
    }
}
