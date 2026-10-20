package com.mythos.reader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mythos.reader.data.importer.LibraryFolder
import com.mythos.reader.model.Book
import com.mythos.reader.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onFilterChange: (LibraryFilter) -> Unit,
    onImport: () -> Unit,
    onOpen: (Book) -> Unit,
    onToggleFavorite: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (LibraryFolder) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var foldersOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minha biblioteca") },
                actions = {
                    IconButton(onClick = { foldersOpen = true }) {
                        Icon(Icons.Outlined.Folder, contentDescription = "Pastas da biblioteca")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Importar") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(selected = state.filter, onSelect = onFilterChange)

            if (state.busyMessage != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            when {
                !state.isLoaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.books.isEmpty() -> EmptyLibrary(state.filter)
                else -> LazyVerticalGrid(
                    // Colunas adaptativas: ~3 na tela externa, 5–6 na interna do Fold.
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onOpen = { onOpen(book) },
                            onToggleFavorite = { onToggleFavorite(book) },
                            onDelete = { pendingDelete = book },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remover livro?") },
            text = { Text("“${book.title}” e o progresso de leitura serão apagados deste aparelho.") },
            confirmButton = {
                TextButton(onClick = { onDelete(book); pendingDelete = null }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }

    if (foldersOpen) {
        ModalBottomSheet(onDismissRequest = { foldersOpen = false }) {
            FoldersSheet(folders = state.folders, onAdd = onAddFolder, onRemove = onRemoveFolder)
        }
    }
}

@Composable
private fun FoldersSheet(
    folders: List<LibraryFolder>,
    onAdd: () -> Unit,
    onRemove: (LibraryFolder) -> Unit,
) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Text("Pastas da biblioteca", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Os EPUBs dessas pastas (e subpastas) entram na biblioteca automaticamente sempre que você abre o app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        folders.forEach { folder ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(folder.name, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onRemove(folder) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Parar de acompanhar ${folder.name}")
                }
            }
        }
        if (folders.isNotEmpty()) {
            Text(
                "Parar de acompanhar uma pasta não remove os livros já importados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Adicionar pasta")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "O Android não deixa escolher a pasta Download inteira. Use uma subpasta, como Download/Livros, ou o botão Importar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterRow(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })) {
        Box {
            Cover(book)
            if (book.favorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorito",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (book.favorite) "Remover dos favoritos" else "Favoritar") },
                    onClick = { menuOpen = false; onToggleFavorite() },
                )
                DropdownMenuItem(text = { Text("Remover") }, onClick = { menuOpen = false; onDelete() })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            statusLabel(book),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Cover(book: Book) {
    val cover by produceState<ImageBitmap?>(null, book.coverPath) {
        value = book.coverPath?.let { path ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
    }
    val shape = RoundedCornerShape(6.dp)
    val modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)

    val image = cover
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun statusLabel(book: Book): String = when (book.status) {
    ReadingStatus.NOT_STARTED -> "Novo"
    ReadingStatus.FINISHED -> "Concluído"
    ReadingStatus.READING -> "${(book.progression * 100).toInt()}%"
}

@Composable
private fun EmptyLibrary(filter: LibraryFilter) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (filter == LibraryFilter.ALL) "Nenhum livro ainda" else "Nada em “${filter.label}”",
            style = MaterialTheme.typography.titleMedium,
        )
        if (filter == LibraryFilter.ALL) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Toque em Importar para escolher arquivos EPUB, TXT ou HTML, ou no ícone de pasta para acompanhar uma pasta do aparelho.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
