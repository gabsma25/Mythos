package com.mythos.reader.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import com.mythos.reader.data.database.BookDao
import com.mythos.reader.model.Book
import com.mythos.reader.model.ExcludedSource
import com.mythos.reader.reader.epub.Readium
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting

sealed interface ImportResult {
    data class Imported(val book: Book) : ImportResult
    data class Duplicate(val existing: Book) : ImportResult
    data class Failed(val message: String) : ImportResult
}

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

    // Importações simultâneas (seletor + sincronização de pastas) não podem
    // passar juntas pela verificação de duplicata.
    private val mutex = Mutex()

    /** @param sourceUri origem a registrar quando o arquivo vem de uma pasta sincronizada. */
    suspend fun import(uri: Uri, sourceUri: String? = null): ImportResult = withContext(Dispatchers.IO) {
        mutex.withLock { importLocked(uri, sourceUri) }
    }

    private suspend fun importLocked(uri: Uri, sourceUri: String?): ImportResult {
        val displayName = displayName(uri) ?: "livro.epub"
        val fileId = UUID.randomUUID().toString()
        val file = File(booksDir, "$fileId.epub")

        val hash = try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return ImportResult.Failed("Não foi possível ler “$displayName”.")
            copyAndHash(input, file)
        } catch (e: Exception) {
            file.delete()
            return ImportResult.Failed("Não foi possível ler “$displayName”.")
        }

        return finishImport(file, fileId, hash, displayName, sourceUri)
    }

    /** TXT e HTML são reconhecidos pelo tipo informado pelo Android ou pela extensão. */
    fun isTextDocument(uri: Uri): Boolean {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val extension = displayName(uri)?.substringAfterLast('.', "")?.lowercase()
        return type in TEXT_TYPES || extension in TEXT_EXTENSIONS
    }

    /**
     * Converte um ou vários TXT/HTML em um único livro EPUB (um capítulo por arquivo) e o importa
     * como qualquer outro. O EPUB gerado é determinístico, então importar o mesmo texto de novo é
     * reconhecido como duplicata.
     */
    suspend fun importText(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sources = uris.map { uri ->
                val name = displayName(uri) ?: "texto.txt"
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        if (data.size > MAX_TEXT_BYTES) {
                            return@withLock ImportResult.Failed("“$name” é grande demais para converter.")
                        }
                        data
                    }
                }.getOrNull() ?: return@withLock ImportResult.Failed("Não foi possível ler “$name”.")
                TextSource(name, bytes)
            }

            val fileId = UUID.randomUUID().toString()
            val file = File(booksDir, "$fileId.epub")
            val title = try {
                TextToEpub.convert(sources, file)
            } catch (e: Exception) {
                file.delete()
                return@withLock ImportResult.Failed("Não foi possível converter o texto em livro.")
            }
            val hash = file.inputStream().use(::hashOf)
            finishImport(file, fileId, hash, "$title.epub", sourceUri = null)
        }
    }

    /** Etapa comum a EPUBs e textos convertidos: duplicata, metadados, capa e registro. */
    private suspend fun finishImport(
        file: File,
        fileId: String,
        hash: String,
        displayName: String,
        sourceUri: String?,
    ): ImportResult {
        bookDao.findByHash(hash)?.let { existing ->
            file.delete()
            // Lembra a origem para que a próxima sincronização não releia este arquivo.
            if (sourceUri != null && existing.sourceUri == null) bookDao.setSourceUri(existing.id, sourceUri)
            return ImportResult.Duplicate(existing)
        }

        val publication = readium.open(file).getOrElse {
            file.delete()
            return ImportResult.Failed("“$displayName” não é um EPUB válido.")
        }

        return try {
            if (!publication.conformsTo(Publication.Profile.EPUB)) {
                file.delete()
                return ImportResult.Failed("“$displayName” não é um EPUB.")
            }

            val coverFile = saveCover(publication, fileId)
            val book = Book(
                title = publication.metadata.title?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.'),
                author = publication.metadata.authors.firstOrNull()?.name,
                filePath = file.absolutePath,
                coverPath = coverFile?.absolutePath,
                addedAt = System.currentTimeMillis(),
                contentHash = hash,
                sourceUri = sourceUri,
            )
            val id = bookDao.insert(book)
            ImportResult.Imported(book.copy(id = id))
        } catch (e: Exception) {
            file.delete()
            ImportResult.Failed("Falha ao importar “$displayName”.")
        } finally {
            publication.close()
        }
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        bookDao.delete(book)
        book.sourceUri?.let { bookDao.exclude(ExcludedSource(it)) }
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
    }

    /** Calcula o hash dos livros importados antes da detecção de duplicatas existir. */
    suspend fun backfillHashes() = withContext(Dispatchers.IO) {
        mutex.withLock {
            bookDao.booksWithoutHash().forEach { book ->
                runCatching { File(book.filePath).inputStream().use(::hashOf) }
                    .onSuccess { bookDao.setHash(book.id, it) }
            }
        }
    }

    /** Lê o arquivo uma única vez, copiando e calculando o SHA-256 ao mesmo tempo. */
    private fun copyAndHash(input: InputStream, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { source ->
            target.outputStream().use { source.copyTo(it) }
        }
        return digest.digest().toHex()
    }

    private fun hashOf(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun saveCover(publication: Publication, fileId: String): File? {
        val bitmap = publication.coverFitting(Size(COVER_WIDTH, COVER_HEIGHT)) ?: return null
        val file = File(coversDir, "$fileId.jpg")
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

    private companion object {
        const val COVER_WIDTH = 480
        const val COVER_HEIGHT = 720
        const val MAX_TEXT_BYTES = 50 * 1024 * 1024
        val TEXT_TYPES = setOf("text/plain", "text/html", "application/xhtml+xml")
        val TEXT_EXTENSIONS = setOf("txt", "html", "htm", "xhtml")
    }
}
