package com.mythos.reader.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookFormat { EPUB }

@Entity(tableName = "books", indices = [Index("contentHash"), Index("sourceUri")])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val format: BookFormat = BookFormat.EPUB,
    /** Cópia privada do arquivo em `filesDir/books`, independente da pasta de origem. */
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    /** Progresso total de 0 a 1, usado apenas para exibição na biblioteca. */
    val progression: Double = 0.0,
    /**
     * Readium Locator serializado em JSON. É a fonte de verdade da posição de leitura,
     * pois o número de páginas muda com a fonte e com a tela (externa ou interna).
     */
    val locatorJson: String? = null,
    val favorite: Boolean = false,
    /** SHA-256 do arquivo; impede importar o mesmo livro duas vezes, venha de onde vier. */
    val contentHash: String? = null,
    /** Documento de origem quando o livro veio de uma pasta sincronizada. */
    val sourceUri: String? = null,
) {
    val status: ReadingStatus
        get() = when {
            progression >= FINISHED_THRESHOLD -> ReadingStatus.FINISHED
            lastOpenedAt == null -> ReadingStatus.NOT_STARTED
            else -> ReadingStatus.READING
        }

    companion object {
        const val FINISHED_THRESHOLD = 0.99
    }
}

enum class ReadingStatus { NOT_STARTED, READING, FINISHED }
