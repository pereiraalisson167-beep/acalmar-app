package br.com.acalmar.agenda.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.BusinessInfo
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

/** Nome e contato que aparecem no orçamento e no termo de consentimento. */
@Composable
fun BusinessScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var professional by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var instagram by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val info = repo.getBusiness()
        name = info.name
        professional = info.professional
        phone = info.phone
        instagram = info.instagram
        loaded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Dados do negócio", onBack = onBack) },
        bottomBar = {
            PrimaryButton(
                text = "Salvar",
                enabled = loaded,
                onClick = {
                    scope.launch {
                        repo.saveBusiness(
                            BusinessInfo(
                                name = name.trim(),
                                professional = professional.trim(),
                                phone = phone.trim(),
                                instagram = instagram.trim()
                            )
                        )
                        notifier.show("Dados do negócio salvos")
                        onBack()
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
            Text(
                "Estes dados aparecem no rodapé do orçamento e no termo de consentimento.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do negócio") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = professional,
                onValueChange = { professional = it },
                label = { Text("Nome da profissional") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("WhatsApp de contato (com DDD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = instagram,
                onValueChange = { instagram = it },
                label = { Text("Instagram (opcional)") },
                placeholder = { Text("@seuperfil") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
