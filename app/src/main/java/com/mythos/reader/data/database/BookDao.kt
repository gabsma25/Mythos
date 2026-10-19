package com.mythos.reader.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.mythos.reader.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Delete
    suspend fun delete(book: Book)

    @Query("UPDATE books SET locatorJson = :locatorJson, progression = :progression WHERE id = :id")
    suspend fun updateProgress(id: Long, locatorJson: String, progression: Double)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun markOpened(id: Long, timestamp: Long)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}
