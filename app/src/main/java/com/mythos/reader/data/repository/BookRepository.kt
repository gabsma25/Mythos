package com.mythos.reader.data.repository

import com.mythos.reader.data.database.BookDao
import com.mythos.reader.data.importer.BookImporter
import com.mythos.reader.model.Book
import kotlinx.coroutines.flow.Flow
import org.readium.r2.shared.publication.Locator

class BookRepository(
    private val bookDao: BookDao,
    private val importer: BookImporter,
) {
    val books: Flow<List<Book>> = bookDao.observeAll()

    suspend fun get(id: Long): Book? = bookDao.get(id)

    suspend fun import(uri: android.net.Uri): Result<Book> = importer.import(uri)

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
