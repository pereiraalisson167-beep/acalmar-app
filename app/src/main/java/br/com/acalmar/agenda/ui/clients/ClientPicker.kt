package br.com.acalmar.agenda.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.domain.formatPhone
import br.com.acalmar.agenda.domain.sortKey
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.Avatar
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

/** Escolha da cliente ao agendar: busca na lista ou cadastro rápido (nome e telefone). */
@Composable
fun ClientPickerDialog(clients: List<Client>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(clients.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val filtered = remember(clients, query) {
        val key = sortKey(query)
        if (key.isBlank()) clients else clients.filter { sortKey(it.name).contains(key) || it.phone.contains(query.trim()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Nova cliente" else "Escolher cliente") },
        text = {
            if (creating) {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nome") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("WhatsApp (com DDD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Os outros dados podem ser preenchidos depois, na ficha da cliente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.BrownSoft
                    )
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar por nome") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                newName = query.trim()
                                creating = true
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Cadastrar nova cliente", color = Brand.Bronze, fontWeight = FontWeight.SemiBold)
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(filtered, key = { it.id }) { client ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onPick(client.id) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(client.name, size = 36)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(client.name, style = MaterialTheme.typography.bodyLarge)
                                    if (client.phone.isNotBlank()) {
                                        Text(
                                            formatPhone(client.phone),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Brand.BrownSoft
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty() && clients.isNotEmpty()) {
                        Text(
                            "Nenhuma cliente encontrada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.BrownSoft,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val id = repo.saveClient(Client(name = newName.trim(), phone = newPhone.trim()))
                            onPick(id)
                        }
                    }
                ) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (creating && clients.isNotEmpty()) creating = false else onDismiss() }) {
                Text(if (creating && clients.isNotEmpty()) "Voltar" else "Cancelar")
            }
        }
    )
}
