package com.mythos.reader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mythos.reader.model.ReaderColumns
import com.mythos.reader.model.ReaderFont
import com.mythos.reader.model.ReaderPreferences
import com.mythos.reader.model.ReaderTextAlign
import com.mythos.reader.model.ReaderTheme
import kotlin.math.roundToInt

private val BackgroundPalette = listOf(
    0xFFFFFFFF, 0xFFFBF8F1, 0xFFF4ECD8, 0xFFFFF4E0, 0xFFE3EDDF, 0xFFDDE7F0,
    0xFFDADADA, 0xFF2A2633, 0xFF1E1E1E, 0xFF10161C, 0xFF000000,
).map { it.toInt() }

private val TextPalette = listOf(
    0xFF000000, 0xFF2B2B2B, 0xFF5B4636, 0xFF2F3B2B, 0xFF1F3550,
    0xFFA8A8A8, 0xFFCFC6B8, 0xFFE8E2D4, 0xFFFFC98B, 0xFFFFFFFF,
).map { it.toInt() }

@Composable
fun ReaderSettings(
    preferences: ReaderPreferences,
    onChange: ((ReaderPreferences) -> ReaderPreferences) -> Unit,
    focusPermissionGranted: Boolean,
    onRequestFocusPermission: () -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section("Tema") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReaderTheme.entries.filter { it != ReaderTheme.CUSTOM }.forEach { theme ->
                    ThemeSwatch(theme, selected = preferences.theme == theme) {
                        onChange { it.copy(theme = theme) }
                    }
                }
                ThemeSwatch(
                    ReaderTheme.CUSTOM,
                    selected = preferences.theme == ReaderTheme.CUSTOM,
                    background = preferences.customBackground,
                    text = preferences.customText,
                ) { onChange { it.copy(theme = ReaderTheme.CUSTOM) } }
            }
        }

        if (preferences.theme == ReaderTheme.CUSTOM) {
            Section("Cor do fundo") {
                ColorRow(BackgroundPalette, preferences.customBackground) { color ->
                    onChange { it.copy(customBackground = color) }
                }
            }
            Section("Cor do texto") {
                ColorRow(TextPalette, preferences.customText) { color ->
                    onChange { it.copy(customText = color) }
                }
            }
        }

        Section("Fonte") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderFont.entries.forEach { font ->
                    FilterChip(
                        selected = preferences.font == font,
                        onClick = { onChange { it.copy(font = font) } },
                        label = { Text(font.label) },
                    )
                }
            }
        }

        PreferenceSlider("Tamanho do texto", preferences.fontSize, 0.6..2.5, steps = 18, format = ::percent) { v ->
            onChange { it.copy(fontSize = v) }
        }
        PreferenceSlider("Entrelinhas", preferences.lineHeight, 1.0..2.2, steps = 11, format = ::decimal) { v ->
            onChange { it.copy(lineHeight = v) }
        }
        PreferenceSlider("Espaço entre parágrafos", preferences.paragraphSpacing, 0.0..2.0, steps = 7, format = ::decimal) { v ->
            onChange { it.copy(paragraphSpacing = v) }
        }
        PreferenceSlider("Espaço entre letras", preferences.letterSpacing, 0.0..0.3, steps = 5, format = ::percent) { v ->
            onChange { it.copy(letterSpacing = v) }
        }
        PreferenceSlider("Espaço entre palavras", preferences.wordSpacing, 0.0..1.0, steps = 9, format = ::percent) { v ->
            onChange { it.copy(wordSpacing = v) }
        }
        PreferenceSlider("Margens", preferences.pageMargins, 0.5..2.5, steps = 7, format = ::decimal) { v ->
            onChange { it.copy(pageMargins = v) }
        }

        Section("Alinhamento") {
            Segmented(ReaderTextAlign.entries, preferences.textAlign, { it.label }) { value ->
                onChange { it.copy(textAlign = value) }
            }
        }

        Section("Colunas") {
            Segmented(ReaderColumns.entries, preferences.columns, { it.label }) { value ->
                onChange { it.copy(columns = value) }
            }
            Text(
                "No modo automático, a tela interna do Fold usa duas colunas quando está na horizontal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Animação ao virar a página", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = preferences.animatedPageTurns,
                onCheckedChange = { checked -> onChange { it.copy(animatedPageTurns = checked) } },
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Não perturbe durante a leitura", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = preferences.doNotDisturb,
                    onCheckedChange = { checked ->
                        onChange { it.copy(doNotDisturb = checked) }
                        if (checked && !focusPermissionGranted) onRequestFocusPermission()
                    },
                )
            }
            if (preferences.doNotDisturb && !focusPermissionGranted) {
                Text(
                    "Falta permitir o acesso ao Não perturbe para o Mythos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRequestFocusPermission, contentPadding = PaddingValues(0.dp)) {
                    Text("Abrir configurações")
                }
            } else {
                Text(
                    "Silencia notificações, sons e vibração enquanto um livro estiver aberto. " +
                        "Alarmes e chamadas repetidas continuam tocando. Ajuste as exceções em " +
                        "Configurações › Modos › Leitura Mythos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Mantém o valor localmente durante o arraste e só aplica ao soltar, evitando refazer a paginação a cada passo. */
@Composable
private fun PreferenceSlider(
    title: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    steps: Int,
    format: (Double) -> String,
    onCommit: (Double) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(format(current.toDouble()), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onCommit(current.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = steps,
        )
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    background: Int = theme.background,
    text: Int = theme.text,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Surface(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
            shape = CircleShape,
            color = Color(background),
            border = BorderStroke(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Aa", color = Color(text), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(theme.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (color == selected) 3.dp else 1.dp,
                        color = if (color == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) }
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(color)),
            )
        }
    }
}

private fun percent(value: Double) = "${(value * 100).roundToInt()}%"

private fun decimal(value: Double) = "%.1f".format(value)
