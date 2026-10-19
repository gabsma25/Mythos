package com.mythos.reader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Arquivo de uma pasta sincronizada cujo livro foi removido da biblioteca.
 * Sem este registro, a próxima sincronização importaria o livro de novo.
 */
@Entity(tableName = "excluded_sources")
data class ExcludedSource(@PrimaryKey val uri: String)
