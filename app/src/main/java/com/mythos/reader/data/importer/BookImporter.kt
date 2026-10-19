package com.mythos.reader.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import com.mythos.reader.data.database.BookDao
import com.mythos.reader.model.Book
import com.mythos.reader.reader.epub.Readium
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting

/**
 * Copia o arquivo escolhido para o armazenamento privado do app, extrai metadados e capa
 * com o Readium e registra o livro na biblioteca. Mover ou apagar o arquivo original
 * depois da importação não interrompe a leitura.
 */
class BookImporter(
    private val context: Context,
    private val readium: Readium,
    private val bookDao: BookDao,
) {
    private val booksDir get() = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }

    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val displayName = displayName(uri) ?: "livro.epub"
        val fileId = UUID.randomUUID().toString()
        val file = File(booksDir, "$fileId.epub")

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Não foi possível ler o arquivo.")
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
        } catch (e: Exception) {
            file.delete()
            return@withContext Result.failure(e)
        }

        val publication = readium.open(file).getOrElse {
            file.delete()
            return@withContext Result.failure(IOException("“$displayName” não é um EPUB válido."))
        }

        try {
            if (!publication.conformsTo(Publication.Profile.EPUB)) {
                file.delete()
                return@withContext Result.failure(IOException("“$displayName” não é um EPUB."))
            }

            val coverFile = saveCover(publication, fileId)
            val book = Book(
                title = publication.metadata.title?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.'),
                author = publication.metadata.authors.firstOrNull()?.name,
                filePath = file.absolutePath,
                coverPath = coverFile?.absolutePath,
                addedAt = System.currentTimeMillis(),
            )
            val id = bookDao.insert(book)
            Result.success(book.copy(id = id))
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        } finally {
            publication.close()
        }
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        bookDao.delete(book)
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
    }

    private suspend fun saveCover(publication: Publication, fileId: String): File? {
        val bitmap = publication.coverFitting(Size(COVER_WIDTH, COVER_HEIGHT)) ?: return null
        val file = File(coversDir, "$fileId.jpg")
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private companion object {
        const val COVER_WIDTH = 480
        const val COVER_HEIGHT = 720
    }
}
