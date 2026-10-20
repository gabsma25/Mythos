package com.mythos.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mythos.reader.AppContainer
import com.mythos.reader.model.Highlight
import com.mythos.reader.model.HighlightColor
import com.mythos.reader.model.ReaderPreferences
import com.mythos.reader.reader.epub.EpubSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

class ReaderViewModel(
    private val container: AppContainer,
    val session: EpubSession,
    initialPreferences: ReaderPreferences,
) : ViewModel() {

    val preferences: StateFlow<ReaderPreferences> = container.readerPreferences.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialPreferences)

    val highlights: StateFlow<List<Highlight>> = container.highlights.forBook(session.book.id)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Novos destaques usam a última cor escolhida. */
    private var lastColor = HighlightColor.YELLOW

    fun onLocatorChanged(locator: Locator) {
        session.lastLocator = locator
        // Escopo da aplicação: a gravação termina mesmo se a tela for fechada logo após virar a página.
        container.applicationScope.launch {
            container.bookRepository.saveProgress(session.book.id, locator)
        }
    }

    fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { container.readerPreferences.update(transform) }
    }

    fun addHighlight(selection: Locator) {
        container.applicationScope.launch {
            container.highlights.add(session.book.id, selection, lastColor)
        }
    }

    fun setHighlightColor(highlight: Highlight, color: HighlightColor) {
        lastColor = color
        container.applicationScope.launch { container.highlights.setColor(highlight.id, color) }
    }

    fun deleteHighlight(highlight: Highlight) {
        container.applicationScope.launch { container.highlights.delete(highlight.id) }
    }
}
