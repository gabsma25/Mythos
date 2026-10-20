package com.mythos.reader.ui.highlights

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mythos.reader.model.BookHighlights
import com.mythos.reader.model.Highlight
import com.mythos.reader.model.HighlightColor

val HighlightColor.composeColor: Color get() = Color(argb)

/** Um trecho destacado: barra na cor do destaque, o texto e onde ele está no livro. */
@Composable
fun HighlightItem(
    highlight: Highlight,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .height(IntrinsicSize.Min)
            .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                .background(highlight.color.composeColor),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
            Text(
                highlight.text,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                locationLabel(highlight),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

fun locationLabel(highlight: Highlight): String =
    listOfNotNull(highlight.chapter?.takeIf { it.isNotBlank() }, "${(highlight.progression * 100).toInt()}%")
        .joinToString(" · ")

/** Bolinhas de cor; a cor atual aparece marcada. */
@Composable
fun HighlightColorPicker(selected: HighlightColor, onSelect: (HighlightColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HighlightColor.entries.forEach { color ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.composeColor)
                    .border(2.dp, if (color == selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) {
                    Icon(Icons.Filled.Check, contentDescription = color.label, tint = Color.Black.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Painel aberto ao tocar num destaque dentro do livro. */
@Composable
fun HighlightEditor(
    highlight: Highlight,
    onColor: (HighlightColor) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Text(
            highlight.text,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Serif,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        HighlightColorPicker(selected = highlight.color, onSelect = onColor)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCopy) { Text("Copiar") }
            OutlinedButton(onClick = onDelete) { Text("Remover destaque") }
        }
    }
}

/**
 * A área "Destaques" da biblioteca: todos os trechos destacados, agrupados por livro.
 * Tocar num trecho abre o livro exatamente nele.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    books: List<BookHighlights>,
    onBack: () -> Unit,
    onOpen: (Highlight) -> Unit,
    onCopy: (Highlight) -> Unit,
    onShareBook: (BookHighlights) -> Unit,
    onDelete: (Highlight) -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Destaques") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar à biblioteca")
                    }
                },
            )
        },
    ) { padding ->
        if (books.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Nenhum destaque ainda", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "No livro, mantenha o dedo sobre uma palavra, ajuste a seleção e toque em Destacar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            books.forEach { entry ->
                item(key = "book-${entry.book.id}") {
                    BookHeader(entry, onShare = { onShareBook(entry) })
                }
                items(entry.highlights.sortedBy { it.progression }, key = { it.id }) { highlight ->
                    HighlightItem(highlight, onOpen = { onOpen(highlight) }) {
                        HighlightMenu(onCopy = { onCopy(highlight) }, onDelete = { onDelete(highlight) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BookHeader(entry: BookHighlights, onShare: () -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(entry.book.author, trechos(entry.highlights.size)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Compartilhar os destaques de ${entry.book.title}")
            }
        }
    }
}

@Composable
private fun HighlightMenu(onCopy: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Opções do destaque")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Copiar") }, onClick = { open = false; onCopy() })
            DropdownMenuItem(text = { Text("Remover") }, onClick = { open = false; onDelete() })
        }
    }
}

fun trechos(count: Int) = if (count == 1) "1 trecho" else "$count trechos"

/** Texto simples com todos os destaques de um livro, na ordem de leitura. */
fun BookHighlights.asShareText(): String = buildString {
    append(book.title)
    book.author?.let { append(" — ").append(it) }
    highlights.sortedBy { it.progression }.forEach { highlight ->
        append("\n\n“").append(highlight.text).append("”")
        highlight.chapter?.takeIf { it.isNotBlank() }?.let { append("\n(").append(it).append(")") }
    }
}

fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}
