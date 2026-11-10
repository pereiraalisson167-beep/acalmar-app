package br.com.acalmar.agenda.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.acalmar.agenda.data.PayMethod
import br.com.acalmar.agenda.domain.digitsOnly
import br.com.acalmar.agenda.domain.formatMoneyPlain
import br.com.acalmar.agenda.domain.initials
import br.com.acalmar.agenda.ui.theme.Brand

private const val DAY_MS = 86_400_000L

/** Barra do topo das telas internas. */
@Composable
fun AcalmarTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

/** Cartão padrão do app: fundo claro com contorno suave. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = Brand.Card,
    border: Color = Brand.Line,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = Brand.Brown)
    val stroke = BorderStroke(1.dp, border)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = stroke, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = stroke, content = content)
    }
}

/** Título de seção em letras pequenas e espaçadas, como no logo. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 3),
        color = Brand.Bronze,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

/** Círculo com as iniciais da cliente. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Int = 44) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brand.Beige),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials(name),
            color = Brand.BronzeDark,
            fontWeight = FontWeight.SemiBold,
            style = if (size >= 60) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall
        )
    }
}

/** Etiqueta pequena colorida (situação do atendimento, pacote, etc.). */
@Composable
fun Tag(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Mensagem para listas vazias. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.BrownSoft,
            textAlign = TextAlign.Center
        )
    }
}

/** Linha clicável de formulário: rótulo em cima, valor embaixo e seta à direita. */
@Composable
fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    placeholder: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Brand.Card,
        border = BorderStroke(1.dp, Brand.Line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Brand.Bronze, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.BrownSoft)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (placeholder) Brand.BrownSoft else Brand.Brown,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Brand.Gold)
        }
    }
}

/** Campo de valor em reais: a pessoa digita só os números e os centavos se ajustam sozinhos. */
@Composable
fun MoneyField(
    cents: Long,
    onChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val text = formatMoneyPlain(cents)
    OutlinedTextField(
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = { v -> onChange(digitsOnly(v.text).take(9).toLongOrNull() ?: 0L) },
        label = { Text(label) },
        prefix = { Text("R$ ") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Campo de número inteiro simples. */
@Composable
fun IntField(
    value: Int?,
    onChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    maxDigits: Int = 3
) {
    val text = value?.toString().orEmpty()
    OutlinedTextField(
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = { v -> onChange(digitsOnly(v.text).take(maxDigits).toIntOrNull()) },
        label = { Text(label) },
        suffix = if (suffix != null) ({ Text(suffix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Chip de escolha no estilo do app. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Brand.Card,
            labelColor = Brand.Brown,
            selectedContainerColor = Brand.Bronze,
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Brand.Line,
            selectedBorderColor = Brand.Bronze
        )
    )
}

/** Escolha da forma de pagamento. */
@Composable
fun PayMethodChips(selected: PayMethod, onSelect: (PayMethod) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PayMethod.entries.forEach { m ->
            ChoiceChip(text = m.label, selected = m == selected, onClick = { onSelect(m) })
        }
    }
}

/** Diálogo de confirmação simples. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancelar",
    danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) Brand.Terracotta else Brand.Bronze, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

/** Diálogo para escolher uma opção de uma lista. */
@Composable
fun <T> PickerDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    sublabel: (T) -> String? = { null },
    selected: T? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    val isSelected = option == selected
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(if (isSelected) Brand.Beige else Color.Transparent)
                            .clickable { onPick(option) }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text(
                            label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        val sub = sublabel(option)
                        if (sub != null) {
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = Brand.BrownSoft)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

/** Calendário para escolher uma data (dia desde 1970). */
@Composable
fun DayPickerDialog(initialDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Math.floorDiv(millis, DAY_MS)) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** Relógio para escolher um horário (minutos desde a meia-noite). */
@Composable
fun MinutePickerDialog(initialMin: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, title: String = "Horário") {
    val safe = initialMin.coerceIn(0, 23 * 60 + 59)
    val state = rememberTimePickerState(initialHour = safe / 60, initialMinute = safe % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Botão principal de largura total. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Par "rótulo: valor" em uma linha. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Brand.Brown) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Brand.BrownSoft, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}
