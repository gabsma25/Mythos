package com.mythos.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mythos.reader.AppContainer
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
}
