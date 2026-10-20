package com.mythos.reader.data.repository

import com.mythos.reader.data.database.HighlightDao
import com.mythos.reader.model.BookHighlights
import com.mythos.reader.model.Highlight
import com.mythos.reader.model.HighlightColor
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

class HighlightRepository(private val dao: HighlightDao) {

    val byBook: Flow<List<BookHighlights>> = dao.observeByBook()

    val count: Flow<Int> = dao.observeCount()

    fun forBook(bookId: Long): Flow<List<Highlight>> = dao.observeForBook(bookId)

    suspend fun get(id: Long): Highlight? = dao.get(id)

    /** Grava o trecho selecionado. Seleções vazias (só espaços) são ignoradas. */
    suspend fun add(bookId: Long, locator: Locator, color: HighlightColor): Long? {
        val text = locator.text.highlight?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return dao.insert(
            Highlight(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                text = text,
                chapter = locator.title,
                progression = locator.locations.totalProgression ?: 0.0,
                color = color,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setColor(id: Long, color: HighlightColor) = dao.setColor(id, color)

    suspend fun delete(id: Long) = dao.delete(id)
}

fun Highlight.locator(): Locator? = runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull()
