package com.mythos.reader.reader.epub

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

fun ReaderPreferences.toEpubPreferences(): EpubPreferences = EpubPreferences(
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
    columnCount = when (columns) {
        ReaderColumns.AUTO -> ColumnCount.AUTO
        ReaderColumns.ONE -> ColumnCount.ONE
        ReaderColumns.TWO -> ColumnCount.TWO
    },
    // Sem isso o Readium ignora entrelinhas, espaçamentos e alinhamento escolhidos.
    publisherStyles = false,
    scroll = false,
)
