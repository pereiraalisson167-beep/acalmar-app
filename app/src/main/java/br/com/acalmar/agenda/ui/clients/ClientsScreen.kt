package br.com.acalmar.agenda.ui.clients

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.Client
import br.com.acalmar.agenda.domain.PackageState
import br.com.acalmar.agenda.domain.RecallItem
import br.com.acalmar.agenda.domain.digitsOnly
import br.com.acalmar.agenda.domain.firstName
import br.com.acalmar.agenda.domain.formatPhone
import br.com.acalmar.agenda.domain.monthName
import br.com.acalmar.agenda.domain.sortKey
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.Avatar
import br.com.acalmar.agenda.ui.components.EmptyState
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.theme.Brand
import java.time.LocalDate

/** Dia e mês do aniversário guardado como "dd/MM" ou "dd/MM/aaaa"; nulo se não houver. */
fun birthdayDayMonth(birthday: String): Pair<Int, Int>? {
    val d = digitsOnly(birthday)
    if (d.length < 4) return null
    val day = d.substring(0, 2).toIntOrNull() ?: return null
    val month = d.substring(2, 4).toIntOrNull() ?: return null
    return if (day in 1..31 && month in 1..12) day to month else null
}

@Composable
fun ClientsScreen(onOpenClient: (Long) -> Unit, onNewClient: () -> Unit, onOpenRecall: () -> Unit) {
    val repo = LocalRepo.current
    val clients by repo.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    val packageStates by repo.packageStates.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val recalls by remember(todayDay) { repo.recalls(todayDay) }.collectAsStateWithLifecycle(initialValue = emptyList())

    val filtered = remember(clients, query) {
        val key = sortKey(query)
        val digits = digitsOnly(query)
        if (key.isBlank()) clients
        else clients.filter {
            sortKey(it.name).contains(key) || (digits.length >= 3 && digitsOnly(it.phone).contains(digits))
        }
    }
    val activeByClient = remember(packageStates, todayDay) {
        packageStates.filter { it.isActive(todayDay) }.groupBy { it.pkg.clientId }
    }
    val birthdays = remember(clients, today.monthValue) {
        clients.mapNotNull { c -> birthdayDayMonth(c.birthday)?.let { (d, m) -> if (m == today.monthValue) d to c else null } }
            .sortedBy { it.first }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text("Clientes", style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (clients.size) {
                        0 -> "Nenhuma cliente cadastrada"
                        1 -> "1 cliente cadastrada"
                        else -> "${clients.size} clientes cadastradas"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar por nome ou telefone") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (recalls.isNotEmpty() && query.isBlank()) {
                    item(key = "recall") { RecallCard(recalls, onClick = onOpenRecall) }
                }
                if (birthdays.isNotEmpty() && query.isBlank()) {
                    item(key = "birthdays") {
                        BirthdaysCard(
                            month = monthName(today),
                            todayDayOfMonth = today.dayOfMonth,
                            list = birthdays,
                            onOpenClient = onOpenClient
                        )
                    }
                }
                items(filtered, key = { it.id }) { client ->
                    ClientRow(client, activeByClient[client.id].orEmpty(), onClick = { onOpenClient(client.id) })
                }
                if (clients.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.Groups,
                            title = "Sua lista de clientes começa aqui",
                            subtitle = "Toque em \"Nova cliente\" para cadastrar a primeira."
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item(key = "none") {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "Nenhuma cliente encontrada",
                            subtitle = "Confira o nome ou o telefone digitado."
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewClient,
            containerColor = Brand.Bronze,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Add, contentDescription = "Nova cliente") },
            text = { Text("Nova cliente") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

/** Atalho para a lista "Hora de chamar de volta". */
@Composable
private fun RecallCard(recalls: List<RecallItem>, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, container = Brand.BeigeSoft, border = Brand.Gold) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hora de chamar de volta", style = MaterialTheme.typography.titleSmall, color = Brand.BronzeDark)
                val names = recalls.take(2).joinToString(", ") { firstName(it.client.name) }
                val more = recalls.size - 2
                Text(
                    if (more > 0) "$names e mais $more" else names,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.BrownSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Tag(recalls.size.toString(), Brand.Bronze, Color.White)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Brand.Gold)
        }
    }
}

@Composable
private fun ClientRow(client: Client, packages: List<PackageState>, onClick: () -> Unit) {
    SoftCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(client.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    client.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (client.phone.isNotBlank()) {
                    Text(formatPhone(client.phone), style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft)
                }
            }
            val pkg = packages.firstOrNull()
            if (pkg != null) {
                Tag("Pacote ${pkg.used}/${pkg.pkg.totalSessions}", Brand.Beige, Brand.BronzeDark)
            }
        }
    }
}

@Composable
private fun BirthdaysCard(
    month: String,
    todayDayOfMonth: Int,
    list: List<Pair<Int, Client>>,
    onOpenClient: (Long) -> Unit
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), container = Brand.BeigeSoft) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cake, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Aniversariantes de $month", style = MaterialTheme.typography.titleSmall, color = Brand.BronzeDark)
            }
            Spacer(Modifier.height(6.dp))
            list.forEach { (day, client) ->
                val isToday = day == todayDayOfMonth
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Dia $day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.BrownSoft,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        client.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isToday) Tag("Hoje", Brand.Bronze, Color.White)
                    androidx.compose.material3.TextButton(onClick = { onOpenClient(client.id) }) {
                        Text("Ver ${firstName(client.name)}", maxLines = 1)
                    }
                }
            }
        }
    }
}
