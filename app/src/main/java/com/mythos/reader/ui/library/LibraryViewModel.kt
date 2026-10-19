package com.mythos.reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mythos.reader.AppContainer
import com.mythos.reader.data.importer.ImportResult
import com.mythos.reader.data.importer.LibraryFolder
import com.mythos.reader.model.Book
import com.mythos.reader.model.ReadingStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("Todos"),
    READING("Lendo"),
    NOT_STARTED("Não iniciados"),
    FINISHED("Concluídos"),
    FAVORITES("Favoritos");

    fun matches(book: Book): Boolean = when (this) {
        ALL -> true
        READING -> book.status == ReadingStatus.READING
        NOT_STARTED -> book.status == ReadingStatus.NOT_STARTED
        FINISHED -> book.status == ReadingStatus.FINISHED
        FAVORITES -> book.favorite
    }
}

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val folders: List<LibraryFolder> = emptyList(),
    val isLoaded: Boolean = false,
    val busyMessage: String? = null,
)

sealed interface LibraryEvent {
    data class OpenReader(val bookId: Long) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.bookRepository
    private val folders = container.libraryFolders
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val busyMessage = MutableStateFlow<String?>(null)
    private var syncJob: Job? = null

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.books, filter, folders.folders, busyMessage) { books, filter, folders, busy ->
            LibraryUiState(
                books = books.filter(filter::matches),
                filter = filter,
                folders = folders,
                isLoaded = true,
                busyMessage = busy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) = filter.update { value }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyMessage.value = if (uris.size == 1) "Importando…" else "Importando ${uris.size} livros…"
            val results = uris.map { repository.import(it) }
            busyMessage.value = null
            summarize(results, fromFolders = false)?.let { _events.send(LibraryEvent.Message(it)) }
        }
    }

    /** Procura EPUBs novos nas pastas acompanhadas. Chamado sempre que a biblioteca volta à tela. */
    fun syncFolders() {
        if (folders.folders.value.isEmpty() || syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            val results = repository.syncFolders()
            summarize(results, fromFolders = true)?.let { _events.send(LibraryEvent.Message(it)) }
        }
    }

    fun addFolder(treeUri: Uri?) {
        treeUri ?: return
        runCatching { folders.add(treeUri) }
            .onFailure {
                viewModelScope.launch { _events.send(LibraryEvent.Message("Não foi possível acessar essa pasta.")) }
                return
            }
        syncFolders()
    }

    fun removeFolder(folder: LibraryFolder) = folders.remove(folder)

    fun open(book: Book) = open(book.id)

    fun open(bookId: Long) {
        viewModelScope.launch {
            busyMessage.value = "Abrindo…"
            container.epubSessions.open(bookId)
                .onSuccess { _events.send(LibraryEvent.OpenReader(bookId)) }
                .onFailure { _events.send(LibraryEvent.Message("Não foi possível abrir o livro.")) }
            busyMessage.value = null
        }
    }

    fun toggleFavorite(book: Book) {
        viewModelScope.launch { repository.setFavorite(book, !book.favorite) }
    }

    fun delete(book: Book) {
        viewModelScope.launch { repository.delete(book) }
    }

    /** Uma mensagem curta com o resultado; nada é dito quando a sincronização não encontra novidades. */
    private fun summarize(results: List<ImportResult>, fromFolders: Boolean): String? {
        val imported = results.filterIsInstance<ImportResult.Imported>()
        val duplicates = results.filterIsInstance<ImportResult.Duplicate>()
        val failures = results.filterIsInstance<ImportResult.Failed>()

        if (results.size == 1 && !fromFolders) {
            return when (val result = results.single()) {
                is ImportResult.Imported -> null
                is ImportResult.Duplicate -> "“${result.existing.title}” já está na biblioteca."
                is ImportResult.Failed -> result.message
            }
        }

        val parts = buildList {
            if (imported.isNotEmpty()) add(plural(imported.size, "livro novo", "livros novos"))
            if (duplicates.isNotEmpty() && !fromFolders) {
                add(plural(duplicates.size, "já estava na biblioteca", "já estavam na biblioteca"))
            }
            if (failures.isNotEmpty()) add(plural(failures.size, "arquivo com erro", "arquivos com erro"))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun plural(count: Int, singular: String, plural: String) =
        "$count ${if (count == 1) singular else plural}"
}
