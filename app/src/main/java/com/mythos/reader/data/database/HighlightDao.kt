package com.mythos.reader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mythos.reader.model.BookHighlights
import com.mythos.reader.model.Highlight
import com.mythos.reader.model.HighlightColor
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY progression, createdAt")
    fun observeForBook(bookId: Long): Flow<List<Highlight>>

    /** Livros com pelo menos um destaque, do mais recentemente destacado ao mais antigo. */
    @Transaction
    @Query(
        """
        SELECT books.* FROM books JOIN highlights ON highlights.bookId = books.id
        GROUP BY books.id ORDER BY MAX(highlights.createdAt) DESC
        """,
    )
    fun observeByBook(): Flow<List<BookHighlights>>

    @Query("SELECT COUNT(*) FROM highlights")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun get(id: Long): Highlight?

    @Insert
    suspend fun insert(highlight: Highlight): Long

    @Query("UPDATE highlights SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: HighlightColor)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}
