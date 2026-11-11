package br.com.acalmar.agenda.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.acalmar.agenda.data.AnamnesisAnswer
import br.com.acalmar.agenda.data.AnamnesisModel
import br.com.acalmar.agenda.data.AnamnesisQuestion
import br.com.acalmar.agenda.data.QuestionType
import br.com.acalmar.agenda.domain.formatDateShort
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.components.AcalmarTopBar
import br.com.acalmar.agenda.ui.components.ChoiceChip
import br.com.acalmar.agenda.ui.components.ConfirmDialog
import br.com.acalmar.agenda.ui.components.PrimaryButton
import br.com.acalmar.agenda.ui.components.SectionLabel
import br.com.acalmar.agenda.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Ficha de anamnese da cliente: respostas às perguntas do modelo. */
@Composable
fun AnamnesisScreen(clientId: Long, onDone: () -> Unit) {
    val repo = LocalRepo.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()

    val client by remember(clientId) { repo.client(clientId) }.collectAsStateWithLifecycle(initialValue = null)
    val template by repo.anamnesisTemplate.collectAsStateWithLifecycle(initialValue = emptyList())
    val answers = remember { mutableStateMapOf<String, AnamnesisAnswer>() }
    var updatedAt by remember { mutableStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(clientId) {
        val saved = repo.getAnamnesis(clientId)
        if (saved != null) {
            answers.putAll(AnamnesisModel.parseAnswers(saved.answersJson))
            updatedAt = saved.updatedAt
        }
        loaded = true
    }

    fun answerOf(question: AnamnesisQuestion): AnamnesisAnswer = answers[question.id] ?: AnamnesisAnswer(question.id)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcalmarTopBar(
                title = "Ficha de anamnese",
                onBack = onDone,
                actions = {
                    if (updatedAt > 0) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Apagar ficha", tint = Brand.Terracotta)
                        }
                    }
                }
            )
        },
        bottomBar = {
            PrimaryButton(
                text = "Salvar ficha",
                enabled = loaded,
                onClick = {
                    scope.launch {
                        repo.saveAnamnesis(clientId, answers.values.toList())
                        notifier.show("Ficha de anamnese salva")
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(client?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (updatedAt > 0) {
                    val date = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    "Atualizada em ${formatDateShort(date)}"
                } else {
                    "Ainda não preenchida"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.BrownSoft
            )

            var lastSection: String? = null
            template.forEach { question ->
                if (question.section != lastSection) {
                    lastSection = question.section
                    Spacer(Modifier.height(18.dp))
                    if (question.section.isNotBlank()) SectionLabel(question.section)
                    Spacer(Modifier.height(4.dp))
                }
                val answer = answerOf(question)
                Column(Modifier.padding(vertical = 8.dp)) {
                    if (question.type == QuestionType.SIM_NAO) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(question.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                ChoiceChip("Sim", selected = answer.yes == true, onClick = {
                                    answers[question.id] = answer.copy(yes = if (answer.yes == true) null else true)
                                })
                                ChoiceChip("Não", selected = answer.yes == false, onClick = {
                                    answers[question.id] = answer.copy(yes = if (answer.yes == false) null else false, text = "")
                                })
                            }
                        }
                        if (answer.yes == true) {
                            OutlinedTextField(
                                value = answer.text,
                                onValueChange = { answers[question.id] = answer.copy(text = it) },
                                label = { Text("Detalhes") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    } else {
                        Text(question.text, style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            value = answer.text,
                            onValueChange = { answers[question.id] = answer.copy(text = it) },
                            placeholder = { Text("Resposta") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                }
                HorizontalDivider(color = Brand.Line)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "As perguntas desta ficha podem ser alteradas em Mais > Fichas e termo.",
                style = MaterialTheme.typography.bodySmall,
                color = Brand.BrownSoft
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Apagar a ficha?",
            text = "Todas as respostas desta cliente serão apagadas.",
            confirmLabel = "Apagar",
            danger = true,
            onConfirm = {
                confirmClear = false
                scope.launch {
                    repo.deleteAnamnesis(clientId)
                    notifier.show("Ficha apagada")
                    onDone()
                }
            },
            onDismiss = { confirmClear = false }
        )
    }
}
