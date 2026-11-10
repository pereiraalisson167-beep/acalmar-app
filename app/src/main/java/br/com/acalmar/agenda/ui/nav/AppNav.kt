package br.com.acalmar.agenda.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.R
import br.com.acalmar.agenda.ui.LocalRepo

// Tela provisória, só para validar o projeto e o build. As telas reais entram em seguida.
@Composable
fun AcalmarRoot() {
    val repo = LocalRepo.current
    val services by repo.services.collectAsStateWithLifecycle(initialValue = emptyList())
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lotus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Text("ACALMAR", style = MaterialTheme.typography.headlineLarge)
            Text("${services.size} serviços cadastrados", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
