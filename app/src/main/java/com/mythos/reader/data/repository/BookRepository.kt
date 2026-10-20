package com.mythos.reader.data.repository

import android.net.Uri
import com.mythos.reader.data.database.BookDao
import com.mythos.reader.data.importer.BookImporter
import com.mythos.reader.data.importer.ImportResult
import com.mythos.reader.data.importer.LibraryFolders
import com.mythos.reader.model.Book
import kotlinx.coroutines.flow.Flow
import org.readium.r2.shared.publication.Locator

class BookRepository(
    private val bookDao: BookDao,
    private val importer: BookImporter,
    private val folders: LibraryFolders,
) {
    val books: Flow<List<Book>> = bookDao.observeAll()

    suspend fun get(id: Long): Book? = bookDao.get(id)

    suspend fun import(uri: Uri): ImportResult = importer.import(uri)

    fun isTextDocument(uri: Uri): Boolean = importer.isTextDocument(uri)

    /** Vários TXT/HTML escolhidos juntos viram um único livro, com um capítulo por arquivo. */
    suspend fun importText(uris: List<Uri>): ImportResult = importer.importText(uris)

    /**
     * Importa os EPUBs novos das pastas acompanhadas. Arquivos já importados (ou removidos
     * da biblioteca de propósito) são pulados sem serem lidos, então a varredura é rápida.
     */
    suspend fun syncFolders(): List<ImportResult> {
        val known = bookDao.knownSources().toHashSet()
        return folders.folders.value
            .flatMap { folders.findEpubs(it) }
            .filter { it.toString() !in known }
            .map { importer.import(it, sourceUri = it.toString()) }
    }

    suspend fun backfillHashes() = importer.backfillHashes()

    suspend fun delete(book: Book) = importer.delete(book)

    suspend fun setFavorite(book: Book, favorite: Boolean) = bookDao.setFavorite(book.id, favorite)

    suspend fun markOpened(id: Long) = bookDao.markOpened(id, System.currentTimeMillis())

    suspend fun saveProgress(id: Long, locator: Locator) {
        bookDao.updateProgress(
            id = id,
            locatorJson = locator.toJSON().toString(),
            progression = locator.locations.totalProgression ?: 0.0,
        )
    }
}
