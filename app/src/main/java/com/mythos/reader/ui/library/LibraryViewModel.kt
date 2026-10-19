package com.mythos.reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mythos.reader.AppContainer
import com.mythos.reader.model.Book
import com.mythos.reader.model.ReadingStatus
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
    val isLoaded: Boolean = false,
    val busyMessage: String? = null,
)

sealed interface LibraryEvent {
    data class OpenReader(val bookId: Long) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.bookRepository
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val busyMessage = MutableStateFlow<String?>(null)

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> =
        combine(repository.books, filter, busyMessage) { books, filter, busy ->
            LibraryUiState(
                books = books.filter(filter::matches),
                filter = filter,
                isLoaded = true,
                busyMessage = busy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) = filter.update { value }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyMessage.value = if (uris.size == 1) "Importando…" else "Importando ${uris.size} livros…"
            val failures = uris.map { repository.import(it) }.filter { it.isFailure }
            busyMessage.value = null
            when {
                failures.isEmpty() && uris.size > 1 -> _events.send(LibraryEvent.Message("${uris.size} livros importados."))
                failures.size == 1 -> _events.send(
                    LibraryEvent.Message(failures.first().exceptionOrNull()?.message ?: "Falha na importação."),
                )
                failures.size > 1 -> _events.send(LibraryEvent.Message("${failures.size} arquivos não puderam ser importados."))
            }
        }
    }

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
}
