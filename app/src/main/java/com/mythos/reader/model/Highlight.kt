package com.mythos.reader.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Cores disponíveis para destacar um trecho. O leitor aplica transparência sobre o texto. */
enum class HighlightColor(val label: String, val argb: Long) {
    YELLOW("Amarelo", 0xFFF2C94C),
    GREEN("Verde", 0xFF6FCF97),
    BLUE("Azul", 0xFF56CCF2),
    PINK("Rosa", 0xFFEB7FB3),
}

/** Um trecho selecionado e destacado durante a leitura. */
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** Readium Locator com o trecho em `text.highlight`; leva de volta ao ponto exato do livro. */
    val locatorJson: String,
    /** Cópia do trecho, para listar os destaques sem abrir o livro. */
    val text: String,
    val chapter: String?,
    /** Posição de 0 a 1 no livro; ordena os destaques na ordem de leitura. */
    val progression: Double,
    val color: HighlightColor = HighlightColor.YELLOW,
    val createdAt: Long,
)

/** Os destaques de um livro, para a área "Destaques" da biblioteca. */
data class BookHighlights(
    @Embedded val book: Book,
    @Relation(parentColumn = "id", entityColumn = "bookId")
    val highlights: List<Highlight>,
)
