package com.mythos.reader.reader.epub

import com.mythos.reader.data.repository.BookRepository
import com.mythos.reader.model.Book
import java.io.File
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Uma publicação aberta e pronta para ser exibida pelo leitor. */
class EpubSession(
    val book: Book,
    val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
    /** Última posição conhecida; usada ao recriar a tela (dobrar/desdobrar, rotação). */
    var lastLocator: Locator?,
)

/**
 * Mantém as publicações abertas em memória. O navegador do Readium é um Fragment que o
 * Android pode recriar, e a sua FragmentFactory precisa da publicação já aberta.
 */
class EpubSessionStore(
    private val readium: Readium,
    private val bookRepository: BookRepository,
) {
    private val sessions = mutableMapOf<Long, EpubSession>()

    fun get(bookId: Long): EpubSession? = sessions[bookId]

    /** @param at posição onde o leitor deve abrir (ex.: um destaque); por padrão, a última lida. */
    suspend fun open(bookId: Long, at: Locator? = null): Result<EpubSession> {
        sessions[bookId]?.let { session ->
            at?.let { session.lastLocator = it }
            return Result.success(session)
        }

        val book = bookRepository.get(bookId)
            ?: return Result.failure(IllegalStateException("Livro não encontrado."))
        val publication = readium.open(File(book.filePath)).getOrElse { return Result.failure(it) }

        val locator = book.locatorJson?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }
        val session = EpubSession(book, publication, EpubNavigatorFactory(publication), at ?: locator)
        sessions[bookId] = session
        bookRepository.markOpened(bookId)
        return Result.success(session)
    }

    fun close(bookId: Long) {
        sessions.remove(bookId)?.publication?.close()
    }
}
