package com.mythos.reader.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderPreferences(
    val font: ReaderFont = ReaderFont.SERIF,
    /** Escala relativa ao tamanho padrão (1.0 = 100%). */
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.5,
    val paragraphSpacing: Double = 0.5,
    val letterSpacing: Double = 0.0,
    val wordSpacing: Double = 0.0,
    val pageMargins: Double = 1.0,
    val textAlign: ReaderTextAlign = ReaderTextAlign.START,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    /** Cores ARGB usadas quando [theme] é [ReaderTheme.CUSTOM]. */
    val customBackground: Int = 0xFFFBF8F1.toInt(),
    val customText: Int = 0xFF2B2B2B.toInt(),
    val columns: ReaderColumns = ReaderColumns.AUTO,
    val animatedPageTurns: Boolean = true,
    /** Liga o modo "Leitura Mythos" (Não Perturbe) enquanto um livro estiver aberto. */
    val doNotDisturb: Boolean = false,
    /** "Duo performático": mostra a capa do livro na tela externa enquanto se lê na interna. */
    val duoCover: Boolean = false,
) {
    val backgroundColor: Int get() = if (theme == ReaderTheme.CUSTOM) customBackground else theme.background
    val textColor: Int get() = if (theme == ReaderTheme.CUSTOM) customText else theme.text
}

/** `cssName` corresponde ao nome de família de fonte aceito pelo Readium. */
@Serializable
enum class ReaderFont(val label: String, val cssName: String?) {
    PUBLISHER("Original", null),
    SERIF("Serifada", "serif"),
    SANS_SERIF("Sem serifa", "sans-serif"),
    MONOSPACE("Monoespaçada", "monospace"),
    OPEN_DYSLEXIC("OpenDyslexic", "OpenDyslexic"),
    ACCESSIBLE_DFA("AccessibleDfA", "AccessibleDfA"),
    IA_WRITER("iA Writer", "IA Writer Duospace"),
}

@Serializable
enum class ReaderTextAlign(val label: String) { START("Esquerda"), JUSTIFY("Justificado") }

@Serializable
enum class ReaderColumns(val label: String) { AUTO("Automático"), ONE("Uma"), TWO("Duas") }

@Serializable
enum class ReaderTheme(val label: String, val background: Int, val text: Int) {
    PAPER("Papel", 0xFFFBF8F1.toInt(), 0xFF2B2B2B.toInt()),
    SEPIA("Sépia", 0xFFF4ECD8.toInt(), 0xFF5B4636.toInt()),
    SAGE("Sálvia", 0xFFE3EDDF.toInt(), 0xFF2F3B2B.toInt()),
    GRAY("Cinza", 0xFFDADADA.toInt(), 0xFF222222.toInt()),
    NIGHT("Noite", 0xFF1E1E1E.toInt(), 0xFFCFC6B8.toInt()),
    BLACK("Preto", 0xFF000000.toInt(), 0xFFA8A8A8.toInt()),
    CUSTOM("Personalizado", 0, 0),
}
