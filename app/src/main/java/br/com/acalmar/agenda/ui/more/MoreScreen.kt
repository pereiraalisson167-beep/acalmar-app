package br.com.acalmar.agenda.ui.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.BuildConfig
import br.com.acalmar.agenda.R
import br.com.acalmar.agenda.data.Keys
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.theme.Brand
import java.util.concurrent.TimeUnit

/** Texto sobre o último backup e se já está na hora de fazer outro. */
fun backupStatus(lastBackupAt: String?): Pair<String, Boolean> {
    val at = lastBackupAt?.toLongOrNull() ?: return "Nenhum backup feito ainda" to true
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - at)
    val text = when {
        days <= 0L -> "Último backup: hoje"
        days == 1L -> "Último backup: ontem"
        else -> "Último backup: há $days dias"
    }
    return text to (days >= 7)
}

@Composable
fun MoreScreen(onServices: () -> Unit, onHours: () -> Unit, onBackup: () -> Unit) {
    val repo = LocalRepo.current
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = emptyMap())
    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val (backupText, backupLate) = backupStatus(settings[Keys.LAST_BACKUP])

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.logo_acalmar),
            contentDescription = "Massoterapia Acalmar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(MaterialTheme.shapes.large)
        )
        Spacer(Modifier.height(4.dp))

        MoreItem(
            icon = Icons.Outlined.Spa,
            title = "Serviços e pacotes",
            subtitle = "${services.count { it.active }} serviços ativos · preços, duração e pacotes",
            onClick = onServices
        )
        MoreItem(
            icon = Icons.Outlined.Schedule,
            title = "Horário de atendimento",
            subtitle = "Dias de trabalho, almoço e intervalo da agenda",
            onClick = onHours
        )
        MoreItem(
            icon = Icons.Outlined.CloudUpload,
            title = "Backup",
            subtitle = backupText + if (backupLate) " · faça um agora" else "",
            highlight = backupLate,
            onClick = onBackup
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Acalmar · versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.BrownSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        container = if (highlight) Brand.BeigeSoft else Brand.Card,
        border = if (highlight) Brand.Gold else Brand.Line
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Brand.Gold)
        }
    }
}
