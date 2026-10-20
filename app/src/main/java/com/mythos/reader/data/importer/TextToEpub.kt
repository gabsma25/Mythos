package com.mythos.reader.data.importer

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.safety.Safelist

/** Um arquivo de texto escolhido pelo usuário. */
class TextSource(val name: String, val bytes: ByteArray) {
    val isHtml: Boolean
        get() = name.substringAfterLast('.', "").lowercase() in setOf("html", "htm", "xhtml")
}

/**
 * Converte TXT e HTML em um EPUB 3 mínimo, para que esses arquivos passem pelo mesmo leitor
 * (Readium), progresso e preferências que os EPUBs.
 *
 * Vários arquivos viram um único livro, com um capítulo por arquivo em ordem natural de nome
 * ("Cap 2" antes de "Cap 10"). Num TXT único, títulos como "Capítulo 3" ou "Chapter 12" dividem
 * os capítulos. A saída é determinística: o mesmo texto gera o mesmo EPUB byte a byte, e a
 * detecção de duplicatas da biblioteca continua funcionando.
 */
object TextToEpub {

    fun convert(sources: List<TextSource>, output: File): String {
        val ordered = sources.sortedWith { a, b -> naturalCompare(a.name, b.name) }
        val title = bookTitle(ordered)
        val chapters = ordered.flatMap { chapters(it, single = ordered.size == 1) }
            .flatMap(::splitLarge)
            .ifEmpty { listOf(Chapter(title, "<p></p>")) }

        writeEpub(output, title, identifier(ordered), chapters)
        return title
    }

    private class Chapter(val title: String, val body: String)

    // ---- Capítulos ------------------------------------------------------------------------

    private fun chapters(source: TextSource, single: Boolean): List<Chapter> {
        val fallbackTitle = source.name.substringBeforeLast('.').trim()
        return if (source.isHtml) {
            listOf(htmlChapter(source, fallbackTitle))
        } else {
            txtChapters(decode(source.bytes), fallbackTitle, single = single)
        }
    }

    private fun txtChapters(text: String, fallbackTitle: String, single: Boolean): List<Chapter> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val headingIndexes = lines.indices.filter { isHeading(lines[it]) }

        // Um título isolado provavelmente é só o nome do capítulo do próprio arquivo.
        if (headingIndexes.size < 2) {
            val (title, bodyLines) = if (headingIndexes.size == 1 && lines.take(headingIndexes[0]).all(String::isBlank)) {
                lines[headingIndexes[0]].trim() to lines.drop(headingIndexes[0] + 1)
            } else {
                fallbackTitle to lines
            }
            return listOf(Chapter(title, paragraphs(bodyLines)))
        }

        val result = mutableListOf<Chapter>()
        val preface = lines.subList(0, headingIndexes.first())
        if (preface.any(String::isNotBlank)) result += Chapter(if (single) "Início" else fallbackTitle, paragraphs(preface))
        headingIndexes.forEachIndexed { i, start ->
            val end = headingIndexes.getOrElse(i + 1) { lines.size }
            result += Chapter(lines[start].trim(), paragraphs(lines.subList(start + 1, end)))
        }
        return result
    }

    private fun isHeading(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length in 1..MAX_HEADING_LENGTH && HEADING.any { it.matches(trimmed) }
    }

    /**
     * Linhas em branco separam parágrafos; quando o texto não tem linhas em branco, cada linha
     * é um parágrafo (formato comum em webnovels salvas como TXT).
     */
    private fun paragraphs(lines: List<String>): String {
        val hasBlankSeparators = lines.any(String::isBlank) && lines.count(String::isNotBlank) > 1
        val blocks: List<List<String>> = if (hasBlankSeparators) {
            lines.fold(mutableListOf(mutableListOf<String>())) { acc, line ->
                if (line.isBlank()) acc.add(mutableListOf()) else acc.last().add(line.trim())
                acc
            }.filter { it.isNotEmpty() }
        } else {
            lines.filter(String::isNotBlank).map { listOf(it.trim()) }
        }
        return blocks.joinToString("\n") { block -> "<p>${block.joinToString("<br/>") { it.escapeXml() }}</p>" }
    }

    private fun htmlChapter(source: TextSource, fallbackTitle: String): Chapter {
        val document = Jsoup.parse(decode(source.bytes))
        val content = mainContent(document)
        val heading = content.selectFirst("h1, h2, h3")?.text()?.takeIf { it.isNotBlank() }
        val title = heading ?: document.title().takeIf { it.isNotBlank() } ?: fallbackTitle

        // Só tags de texto; scripts, menus, formulários e imagens externas (o app é offline) saem.
        val safelist = Safelist.relaxed()
            .removeTags("a")
            .addProtocols("img", "src", "data")
        val clean = Jsoup.parseBodyFragment(Jsoup.clean(content.html(), safelist))
        clean.select("img").forEach { if (!it.attr("src").startsWith("data:")) it.remove() }
        clean.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false)

        // O título vira o <h2> do capítulo; não o repete no corpo.
        if (heading != null) clean.selectFirst("h1, h2, h3")?.remove()
        return Chapter(title, clean.body().html())
    }

    /** O bloco com mais texto em parágrafos: em páginas salvas, o capítulo e não o menu do site. */
    private fun mainContent(document: Document): Element {
        document.select("script, style, noscript, iframe, form, nav, header, footer, aside, button").remove()
        document.selectFirst("article, main, [role=main]")?.let { return it }
        val best = document.select("p").mapNotNull { it.parent() }
            .groupingBy { it }
            .fold(0) { total, _ -> total + 1 }
            .maxByOrNull { (element, _) -> element.select("> p").sumOf { it.text().length } }
            ?.key
        return best ?: document.body()
    }

    /** Capítulos enormes deixam a paginação lenta; são divididos em partes nos limites de parágrafo. */
    private fun splitLarge(chapter: Chapter): List<Chapter> {
        if (chapter.body.length <= MAX_CHAPTER_CHARS) return listOf(chapter)
        val parts = mutableListOf(StringBuilder())
        chapter.body.split("</p>").filter(String::isNotBlank).forEach { piece ->
            if (parts.last().length > MAX_CHAPTER_CHARS) parts += StringBuilder()
            parts.last().append(piece).append("</p>")
        }
        return parts.mapIndexed { i, part ->
            Chapter(if (i == 0) chapter.title else "${chapter.title} (${i + 1})", part.toString())
        }
    }

    // ---- Texto ----------------------------------------------------------------------------

    /** UTF-8 (com ou sem BOM), UTF-16 com BOM, ou Windows-1252 para TXT antigos. */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    private fun bookTitle(sources: List<TextSource>): String {
        val names = sources.map { it.name.substringBeforeLast('.').trim() }
        if (names.size == 1) {
            val single = sources.single()
            if (single.isHtml) Jsoup.parse(decode(single.bytes)).title().takeIf { it.isNotBlank() }?.let { return it }
            return names.single()
        }
        // "Minha Novel - Cap 1", "Minha Novel - Cap 2" → "Minha Novel"
        val prefix = names.reduce { a, b -> a.commonPrefixWith(b, ignoreCase = true) }
            .trimEnd { it.isDigit() || it.isWhitespace() || it in "-_.:#([" }
            .replace(TRAILING_CHAPTER_WORD, "")
            .trimEnd { it.isWhitespace() || it in "-_.:#([" }
        return prefix.takeIf { it.length >= 3 } ?: names.first()
    }

    private fun identifier(sources: List<TextSource>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sources.forEach { digest.update(it.name.toByteArray()); digest.update(it.bytes) }
        return "urn:mythos:" + digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun naturalCompare(a: String, b: String): Int {
        val partsA = NATURAL_PARTS.findAll(a.lowercase()).map { it.value }.toList()
        val partsB = NATURAL_PARTS.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(partsA.size, partsB.size)) {
            val x = partsA[i]
            val y = partsB[i]
            val cmp = if (x[0].isDigit() && y[0].isDigit()) {
                x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 }
                    ?: x.trimStart('0').compareTo(y.trimStart('0'))
            } else {
                x.compareTo(y)
            }
            if (cmp != 0) return cmp
        }
        return partsA.size.compareTo(partsB.size)
    }

    private fun String.escapeXml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---- EPUB -----------------------------------------------------------------------------

    private fun writeEpub(output: File, title: String, identifier: String, chapters: List<Chapter>) {
        val ids = chapters.indices.map { "c%03d".format(it + 1) }
        val safeTitle = title.escapeXml()

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            // O "mimetype" precisa ser a primeira entrada, sem compressão.
            zip.putStored("mimetype", "application/epub+zip".toByteArray())
            zip.put("META-INF/container.xml", CONTAINER)
            zip.put("OEBPS/style.css", STYLE)
            zip.put("OEBPS/nav.xhtml", navDocument(safeTitle, chapters, ids))
            chapters.forEachIndexed { i, chapter -> zip.put("OEBPS/${ids[i]}.xhtml", chapterDocument(chapter)) }
            zip.put("OEBPS/content.opf", packageDocument(safeTitle, identifier, ids))
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
        write(content.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putStored(name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
            time = FIXED_TIME
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    // Montado linha a linha, sem trimIndent()/trimMargin(): o texto inserido não tem indentação
    // e o trimIndent deixaria espaços antes do <?xml?>, tornando o documento inválido; o
    // trimMargin cortaria linhas do próprio capítulo que começassem com "|".
    private fun chapterDocument(chapter: Chapter): String = listOf(
        XML_DECLARATION,
        "<!DOCTYPE html>",
        "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">",
        "<head><meta charset=\"utf-8\"/><title>${chapter.title.escapeXml()}</title>" +
            "<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head>",
        "<body><section epub:type=\"chapter\"><h2>${chapter.title.escapeXml()}</h2>",
        chapter.body,
        "</section></body>",
        "</html>",
    ).joinToString("\n")

    private fun navDocument(title: String, chapters: List<Chapter>, ids: List<String>): String = (
        listOf(
            XML_DECLARATION,
            "<!DOCTYPE html>",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">",
            "<head><meta charset=\"utf-8\"/><title>$title</title></head>",
            "<body><nav epub:type=\"toc\"><h1>Sumário</h1><ol>",
        ) +
            chapters.indices.map { "<li><a href=\"${ids[it]}.xhtml\">${chapters[it].title.escapeXml()}</a></li>" } +
            listOf("</ol></nav></body>", "</html>")
        ).joinToString("\n")

    private fun packageDocument(title: String, identifier: String, ids: List<String>): String = (
        listOf(
            XML_DECLARATION,
            "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\">",
            "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">",
            "<dc:identifier id=\"bookid\">$identifier</dc:identifier>",
            "<dc:title>$title</dc:title>",
            "<dc:language>und</dc:language>",
            "<meta property=\"dcterms:modified\">2000-01-01T00:00:00Z</meta>",
            "</metadata>",
            "<manifest>",
            "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>",
            "<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>",
        ) +
            ids.map { "<item id=\"$it\" href=\"$it.xhtml\" media-type=\"application/xhtml+xml\"/>" } +
            listOf("</manifest>", "<spine>") +
            ids.map { "<itemref idref=\"$it\"/>" } +
            listOf("</spine>", "</package>")
        ).joinToString("\n")

    private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"

    private const val MAX_HEADING_LENGTH = 80
    private const val MAX_CHAPTER_CHARS = 120_000
    private const val FIXED_TIME = 946_684_800_000L // 2000-01-01, para o ZIP sair sempre igual

    private val HEADING = listOf(
        Regex(
            "^(chapter|chap\\.|cap[ií]tulo|cap\\.|part|parte|prologue|pr[oó]logo|epilogue|ep[ií]logo|" +
                "interlude|interl[uú]dio|afterword|posf[aá]cio|side story|extra)\\b.*",
            RegexOption.IGNORE_CASE,
        ),
        Regex("^第[0-9一二三四五六七八九十百千零〇]+[章话話回節节].*"),
        Regex("^#{1,3}\\s+\\S.*"),
    )
    private val TRAILING_CHAPTER_WORD = Regex("(?i)\\b(chapter|chap|cap[ií]tulo|cap|parte?|ep)\\s*$")
    private val NATURAL_PARTS = Regex("\\d+|\\D+")

    private const val CONTAINER = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""

    private const val STYLE = """h2 { margin: 1.5em 0 1em; text-align: center; }
p { margin: 0 0 0.8em; }
img { max-width: 100%; height: auto; }"""
}
