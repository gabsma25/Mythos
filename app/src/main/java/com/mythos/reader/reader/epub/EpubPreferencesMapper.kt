package com.mythos.reader.reader.epub

import android.content.res.Configuration
import androidx.core.graphics.ColorUtils
import com.mythos.reader.model.ReaderColumns
import com.mythos.reader.model.ReaderPreferences
import com.mythos.reader.model.ReaderTextAlign
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

val ReaderPreferences.isDark: Boolean
    get() = ColorUtils.calculateLuminance(backgroundColor) < 0.4

/**
 * Duas páginas lado a lado, como um livro aberto. No modo automático, só com a janela larga
 * na horizontal (tela interna do Fold deitada); a externa deitada é estreita demais.
 */
fun ReaderPreferences.isBookSpread(configuration: Configuration): Boolean = when (columns) {
    ReaderColumns.ONE -> false
    ReaderColumns.TWO -> true
    ReaderColumns.AUTO -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= BOOK_SPREAD_MIN_WIDTH_DP
}

/** Limite da classe de largura "expandida" do Material 3. */
private const val BOOK_SPREAD_MIN_WIDTH_DP = 840

/**
 * @param spread resultado de [isBookSpread]; decidido aqui, e não pelo modo automático do
 * Readium, para que a lombada desenhada pelo app coincida sempre com as colunas. Abaixo de
 * 960 dp, as duas colunas dependem do CSS injetado em IllustrationTransformer.
 */
fun ReaderPreferences.toEpubPreferences(spread: Boolean): EpubPreferences = EpubPreferences(
    fontFamily = font.cssName?.let(::FontFamily),
    fontSize = fontSize,
    lineHeight = lineHeight,
    paragraphSpacing = paragraphSpacing,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    pageMargins = pageMargins,
    textAlign = when (textAlign) {
        ReaderTextAlign.START -> TextAlign.START
        ReaderTextAlign.JUSTIFY -> TextAlign.JUSTIFY
    },
    // O tema base define estilos auxiliares (links, seleção); as cores exatas vêm a seguir.
    theme = if (isDark) Theme.DARK else Theme.LIGHT,
    backgroundColor = Color(backgroundColor),
    textColor = Color(textColor),
    columnCount = if (spread) ColumnCount.TWO else ColumnCount.ONE,
    // Sem isso o Readium ignora entrelinhas, espaçamentos e alinhamento escolhidos.
    publisherStyles = false,
    scroll = false,
)
