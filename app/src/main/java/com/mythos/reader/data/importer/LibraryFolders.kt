package com.mythos.reader.data.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LibraryFolder(val uri: Uri, val name: String)

/**
 * Pastas do aparelho que o app acompanha. As próprias permissões persistentes do
 * Storage Access Framework são a fonte de verdade: o app só enxerga as pastas que
 * você escolheu, sem pedir acesso a todo o armazenamento.
 */
class LibraryFolders(context: Context) {

    private val resolver = context.contentResolver
    private val _folders = MutableStateFlow(load())
    val folders: StateFlow<List<LibraryFolder>> = _folders.asStateFlow()

    fun add(treeUri: Uri) {
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        _folders.value = load()
    }

    fun remove(folder: LibraryFolder) {
        runCatching { resolver.releasePersistableUriPermission(folder.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        _folders.value = load()
    }

    /** Lista recursivamente os EPUBs de uma pasta. */
    suspend fun findEpubs(folder: LibraryFolder): List<Uri> = withContext(Dispatchers.IO) {
        val tree = folder.uri
        val found = mutableListOf<Uri>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)

        while (pending.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeLast())
            // Uma pasta apagada ou com permissão revogada não deve interromper as outras.
            runCatching {
                resolver.query(children, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val name = cursor.getString(1).orEmpty()
                        val mime = cursor.getString(2)
                        when {
                            mime == Document.MIME_TYPE_DIR -> pending.add(id)
                            mime == EPUB_MIME || name.endsWith(".epub", ignoreCase = true) ->
                                found += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        }
                    }
                }
            }
        }
        found
    }

    private fun load(): List<LibraryFolder> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { LibraryFolder(it.uri, displayName(it.uri)) }

    /** "primary:Documents/Livros" vira "Documents/Livros". */
    private fun displayName(treeUri: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        return id.substringAfter(':').ifBlank { id }
    }

    private companion object {
        const val EPUB_MIME = "application/epub+zip"
    }
}
