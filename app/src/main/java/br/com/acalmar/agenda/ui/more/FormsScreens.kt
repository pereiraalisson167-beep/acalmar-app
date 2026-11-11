package br.com.acalmar.agenda.ui.more

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.AnamnesisModel
import br.com.acalmar.agenda.data.AnamnesisQuestion
import br.com.acalmar.agenda.data.ConsentModel
import br.com.acalmar.agenda.data.QuestionType
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.components.SoftCard
import br.com.acalmar.agenda.ui.components.Tag
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch

/** Entrada para os dois modelos: perguntas da anamnese e texto do termo. */
@Composable
fun FormsScreen(onBack: () -> Unit, onAnamnesis: () -> Unit, onConsent: () -> Unit) {
    val repo = LocalRepo.current
    val template by repo.anamnesisTemplate.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcalmarTopBar("Fichas e termo", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormEntry(
                icon = Icons.Outlined.HealthAndSafety,
                title = "Perguntas da ficha de anamnese",
                subtitle = "${template.size} perguntas · incluir, alterar, remover e ordenar",
                onClick = onAnamnesis
            )
            FormEntry(
                icon = Icons.Outlined.Gesture,
                title = "Texto do termo de consentimento",
                subtitle = "O texto que a cliente lê e assina na tela",
                onClick = onConsent
            )
            Text(
                "A ficha e o termo de cada cliente ficam na ficha dela, na aba Clientes. " +
                    "Os dois vêm com um modelo padrão, que deve ser lido e ajustado para o seu atendimento.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun FormEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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

// ---------------------------------------------------------------------------------------------
// Perguntas da anamnese
// ---------------------------------------------------------------------------------------------

/** Modelo da ficha de anamnese. Cada alteração é gravada na hora. */
@Composable
fun AnamnesisTemplateScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val template by repo.anamnesisTemplate.collectAsStateWithLifecycle(initialValue = emptyList())

    var editing by remember { mutableStateOf<AnamnesisQuestion?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun save(list: List<AnamnesisQuestion>) {
        scope.launch { repo.saveAnamnesisTemplate(list) }
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index !in template.indices || target !in template.indices) return
        val list = template.toMutableList()
        val moved = list.removeAt(index)
        // Ao trocar de lugar, a pergunta passa a fazer parte da seção da vizinha.
        list.add(target, moved.copy(section = template[target].section))
        save(list)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = "Perguntas da anamnese",
                onBack = onBack,
                actions = { TextButton(onClick = { confirmReset = true }) { Text("Modelo padrão") } }
            )
        }
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
                itemsIndexed(template, key = { _, question -> question.id }) { index, question ->
                    Column {
                        if (index == 0 || template[index - 1].section != question.section) {
                            SectionLabel(
                                question.section.ifBlank { "Sem seção" },
                                Modifier.padding(top = if (index == 0) 4.dp else 14.dp, bottom = 8.dp)
                            )
                        }
                        SoftCard(Modifier.fillMaxWidth(), onClick = { editing = question }) {
                            Row(
                                Modifier.padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(question.text, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Tag(
                                            if (question.type == QuestionType.SIM_NAO) "Sim ou não" else "Resposta escrita",
                                            Brand.Beige, Brand.BronzeDark
                                        )
                                        if (question.type == QuestionType.SIM_NAO && question.alert.isNotBlank()) {
                                            Tag("Aviso: ${question.alert}", Brand.TerracottaSoft, Brand.Terracotta)
                                        }
                                    }
                                }
                                Column {
                                    IconButton(onClick = { move(index, -1) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Subir pergunta", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { move(index, 1) },
                                        enabled = index < template.lastIndex,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Descer pergunta", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = Brand.Bronze,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Nova pergunta") },
                text = { Text("Nova pergunta") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    val current = editing
    if (current != null || creating) {
        QuestionDialog(
            question = current,
            sections = template.map { it.section }.filter { it.isNotBlank() }.distinct(),
            onDismiss = {
                editing = null
                creating = false
            },
            onSave = { text, type, section, alert ->
                if (current != null) {
                    save(template.map { if (it.id == current.id) it.copy(text = text, type = type, section = section, alert = alert) else it })
                } else {
                    val added = AnamnesisQuestion(AnamnesisModel.newId(template), section, text, type, alert)
                    // A pergunta nova entra no fim da seção escolhida (ou no fim da ficha).
                    val last = template.indexOfLast { it.section == section }
                    val list = template.toMutableList()
                    if (last >= 0) list.add(last + 1, added) else list.add(added)
                    save(list)
                }
                editing = null
                creating = false
            },
            onDelete = {
                if (current != null) save(template.filter { it.id != current.id })
                editing = null
                creating = false
            }
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Voltar para o modelo padrão?",
            text = "As perguntas voltam a ser as do modelo que veio com o app. As respostas já dadas pelas clientes às perguntas padrão são mantidas.",
            confirmLabel = "Usar o padrão",
            onConfirm = {
                confirmReset = false
                scope.launch {
                    repo.resetAnamnesisTemplate()
                    notifier.show("Modelo padrão restaurado")
                }
            },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun QuestionDialog(
    question: AnamnesisQuestion?,
    sections: List<String>,
    onDismiss: () -> Unit,
    onSave: (text: String, type: QuestionType, section: String, alert: String) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember { mutableStateOf(question?.text.orEmpty()) }
    var type by remember { mutableStateOf(question?.type ?: QuestionType.SIM_NAO) }
    var section by remember { mutableStateOf(question?.section ?: sections.lastOrNull().orEmpty()) }
    var alert by remember { mutableStateOf(question?.alert.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (question == null) "Nova pergunta" else "Alterar pergunta") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Pergunta") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Sim ou não", selected = type == QuestionType.SIM_NAO, onClick = { type = QuestionType.SIM_NAO })
                    ChoiceChip("Resposta escrita", selected = type == QuestionType.TEXTO, onClick = { type = QuestionType.TEXTO })
                }
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Seção") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                if (sections.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sections.forEach { name ->
                            ChoiceChip(name, selected = section == name, onClick = { section = name })
                        }
                    }
                }
                if (type == QuestionType.SIM_NAO) {
                    OutlinedTextField(
                        value = alert,
                        onValueChange = { alert = it },
                        label = { Text("Aviso de atenção (opcional)") },
                        supportingText = { Text("Palavra curta que aparece na ficha e no atendimento quando a resposta é \"sim\". Ex.: Alergia.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (question != null) {
                    TextButton(onClick = onDelete) { Text("Remover pergunta", color = Brand.Terracotta) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text.trim(), type, section.trim(), if (type == QuestionType.SIM_NAO) alert.trim() else "") }
            ) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Texto do termo de consentimento
// ---------------------------------------------------------------------------------------------

@Composable
fun ConsentTextScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        text = repo.getConsentText()
        loaded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = "Texto do termo",
                onBack = onBack,
                actions = { TextButton(onClick = { text = ConsentModel.DEFAULT_TEXT }) { Text("Texto padrão") } }
            )
        },
        bottomBar = {
            PrimaryButton(
                text = "Salvar texto",
                enabled = loaded && text.isNotBlank(),
                onClick = {
                    scope.launch {
                        if (text.trim() == ConsentModel.DEFAULT_TEXT) repo.resetConsentText() else repo.saveConsentText(text)
                        notifier.show("Texto do termo salvo")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Este é um modelo. Leia, ajuste para o seu atendimento e, se possível, peça a revisão de um profissional. " +
                    "Na hora de assinar, {cliente}, {profissional} e {negocio} são trocados pelos nomes. " +
                    "A autorização de uso de imagem é perguntada à parte, na tela de assinatura.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
