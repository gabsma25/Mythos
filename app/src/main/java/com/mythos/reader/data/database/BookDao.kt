package com.mythos.reader.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mythos.reader.model.Book
import com.mythos.reader.model.ExcludedSource
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): Book?

    @Query("SELECT * FROM books WHERE contentHash IS NULL")
    suspend fun booksWithoutHash(): List<Book>

    /** Origens já conhecidas: livros importados de pastas e livros removidos de propósito. */
    @Query("SELECT sourceUri FROM books WHERE sourceUri IS NOT NULL UNION SELECT uri FROM excluded_sources")
    suspend fun knownSources(): List<String>

    @Insert
    suspend fun insert(book: Book): Long

    @Delete
    suspend fun delete(book: Book)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun exclude(source: ExcludedSource)

    @Query("UPDATE books SET contentHash = :hash WHERE id = :id")
    suspend fun setHash(id: Long, hash: String)

    @Query("UPDATE books SET sourceUri = :sourceUri WHERE id = :id")
    suspend fun setSourceUri(id: Long, sourceUri: String)

    @Query("UPDATE books SET locatorJson = :locatorJson, progression = :progression WHERE id = :id")
    suspend fun updateProgress(id: Long, locatorJson: String, progression: Double)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun markOpened(id: Long, timestamp: Long)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}
