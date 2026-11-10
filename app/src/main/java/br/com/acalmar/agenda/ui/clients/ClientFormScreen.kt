package br.com.acalmar.agenda.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.domain.isValidBirthday
import br.com.acalmar.agenda.domain.maskBirthday
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import kotlinx.coroutines.launch

private val HOW_FOUND = listOf("Indicação", "Instagram", "WhatsApp", "Google", "Passou em frente", "Já era cliente")

@Composable
fun ClientFormScreen(clientId: Long, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val editing = clientId != 0L

    var original by remember { mutableStateOf<Client?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var howFound by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(clientId) {
        if (editing) {
            repo.getClient(clientId)?.let { c ->
                original = c
                name = c.name
                phone = c.phone
                birthday = c.birthday
                howFound = c.howFound
                notes = c.notes
            }
        }
    }

    val birthdayOk = isValidBirthday(birthday)
    val canSave = name.isNotBlank() && birthdayOk

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar(if (editing) "Editar cliente" else "Nova cliente", onBack = onDone) },
        bottomBar = {
            PrimaryButton(
                text = "Salvar",
                enabled = canSave,
                onClick = {
                    scope.launch {
                        val base = original ?: Client(name = "")
                        repo.saveClient(
                            base.copy(
                                name = name.trim(),
                                phone = phone.trim(),
                                birthday = birthday,
                                howFound = howFound.trim(),
                                notes = notes.trim()
                            )
                        )
                        notifier.show(if (editing) "Cliente atualizada" else "Cliente cadastrada")
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
                label = { Text("Nome") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("WhatsApp (com DDD)") },
                placeholder = { Text("11 99999-9999") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = TextFieldValue(birthday, selection = TextRange(birthday.length)),
                onValueChange = { birthday = maskBirthday(it.text) },
                label = { Text("Aniversário") },
                placeholder = { Text("dd/mm ou dd/mm/aaaa") },
                singleLine = true,
                isError = !birthdayOk,
                supportingText = {
                    Text(if (birthdayOk) "O ano é opcional." else "Data incompleta ou inválida.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Como conheceu você")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HOW_FOUND.forEach { option ->
                    ChoiceChip(option, selected = howFound == option, onClick = {
                        howFound = if (howFound == option) "" else option
                    })
                }
            }
            OutlinedTextField(
                value = howFound,
                onValueChange = { howFound = it },
                label = { Text("Outro (ou quem indicou)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observações") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
