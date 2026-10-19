package com.mythos.reader

import android.app.Application
import android.content.Context
import com.mythos.reader.data.database.MythosDatabase
import com.mythos.reader.data.importer.BookImporter
import com.mythos.reader.data.importer.LibraryFolders
import com.mythos.reader.data.preferences.ReaderPreferencesRepository
import com.mythos.reader.data.repository.BookRepository
import com.mythos.reader.reader.epub.EpubSessionStore
import com.mythos.reader.reader.epub.Readium
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MythosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Injeção de dependências manual: o app é pequeno o bastante para dispensar um framework. */
class AppContainer(context: Context) {
    /** Para gravações que não devem ser canceladas junto com uma tela. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = MythosDatabase.create(context)
    private val readium = Readium(context)

    val libraryFolders = LibraryFolders(context)
    val bookRepository = BookRepository(
        bookDao = database.bookDao(),
        importer = BookImporter(context, readium, database.bookDao()),
        folders = libraryFolders,
    )
    val readerPreferences = ReaderPreferencesRepository(context)
    val epubSessions = EpubSessionStore(readium, bookRepository)

    init {
        applicationScope.launch { bookRepository.backfillHashes() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MythosApp).container
