package com.mythos.reader.data.importer

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TextToEpubTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun convert(vararg sources: TextSource): Pair<String, File> {
        val out = tmp.newFile("livro-${System.nanoTime()}.epub")
        return TextToEpub.convert(sources.toList(), out) to out
    }

    private fun txt(name: String, text: String) = TextSource(name, text.toByteArray())

    private fun chapterTitles(epub: File): List<String> = ZipFile(epub).use { zip ->
        zip.entries().toList().map { it.name }.filter { Regex("OEBPS/c\\d+\\.xhtml").matches(it) }.sorted()
            .map { name ->
                val xml = zip.getInputStream(zip.getEntry(name)).readBytes().decodeToString()
                Regex("<h2>(.*?)</h2>").find(xml)!!.groupValues[1]
            }
    }

    private fun assertValidEpub(epub: File) = ZipFile(epub).use { zip ->
        val entries = zip.entries().toList()
        assertEquals("mimetype precisa ser a primeira entrada", "mimetype", entries.first().name)
        assertEquals(ZipEntry.STORED, entries.first().method)
        assertTrue(zip.getEntry("META-INF/container.xml") != null)
        // Todo documento precisa ser XML bem formado, senão o Readium não consegue exibir.
        val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        entries.filter { it.name.endsWith(".xhtml") || it.name.endsWith(".opf") || it.name.endsWith(".xml") }
            .forEach { entry -> zip.getInputStream(entry).use { parser.parse(it) } }
    }

    @Test
    fun `TXT unico e dividido nos titulos de capitulo`() {
        val text = """
            Uma nota do autor antes de tudo.

            Capítulo 1 — O começo
            Primeiro parágrafo.

            Segundo parágrafo com <símbolos> & "aspas".

            Capítulo 2 — O meio
            Texto do meio.

            Capítulo 3 — O fim
            Texto final.
        """.trimIndent()
        val (_, epub) = convert(txt("Minha história.txt", text))

        assertValidEpub(epub)
        assertEquals(
            listOf("Início", "Capítulo 1 — O começo", "Capítulo 2 — O meio", "Capítulo 3 — O fim"),
            chapterTitles(epub),
        )
    }

    @Test
    fun `mesmo texto gera exatamente o mesmo EPUB`() {
        val source = txt("Livro.txt", "Capítulo 1\nUm.\n\nCapítulo 2\nDois.")
        val (_, a) = convert(source)
        val (_, b) = convert(source)
        assertArrayEquals(a.readBytes(), b.readBytes())
    }

    @Test
    fun `varios arquivos viram capitulos em ordem natural e o titulo e o prefixo comum`() {
        val (title, epub) = convert(
            txt("Minha Novel - Cap 10.txt", "Dez."),
            txt("Minha Novel - Cap 2.txt", "Dois."),
            txt("Minha Novel - Cap 1.txt", "Um."),
        )
        assertValidEpub(epub)
        assertEquals("Minha Novel", title)
        assertEquals(
            listOf("Minha Novel - Cap 1", "Minha Novel - Cap 2", "Minha Novel - Cap 10"),
            chapterTitles(epub),
        )
    }

    @Test
    fun `HTML salvo mantem o texto e descarta menus e scripts`() {
        val html = """
            <html><head><title>Página salva</title><script>alert(1)</script></head>
            <body>
              <nav><a href="/">Início</a> <a href="/next">Próximo</a></nav>
              <div class="content">
                <h1>Capítulo 7</h1>
                <p>Primeiro parágrafo do capítulo.</p>
                <p>Segundo parágrafo, com <em>ênfase</em> e uma imagem externa <img src="https://x/y.png">.</p>
                <p>Terceiro<br>com quebra.</p>
              </div>
              <footer>Copyright do site</footer>
            </body></html>
        """.trimIndent()
        val (title, epub) = convert(TextSource("pagina.html", html.toByteArray()))
        assertValidEpub(epub)
        assertEquals("Página salva", title)
        assertEquals(listOf("Capítulo 7"), chapterTitles(epub))

        val body = ZipFile(epub).use { it.getInputStream(it.getEntry("OEBPS/c001.xhtml")).readBytes().decodeToString() }
        assertTrue(body.contains("Primeiro parágrafo do capítulo."))
        assertTrue(body.contains("<em>ênfase</em>"))
        assertFalse(body.contains("alert"))
        assertFalse(body.contains("Próximo"))
        assertFalse(body.contains("Copyright do site"))
        assertFalse("imagem externa não funciona offline", body.contains("https://x/y.png"))
    }

    @Test
    fun `linhas do texto que comecam com barra vertical sao preservadas`() {
        val (_, epub) = convert(txt("Tabela.txt", "| coluna | valor |\n| a | 1 |"))
        assertValidEpub(epub)
        val body = ZipFile(epub).use { it.getInputStream(it.getEntry("OEBPS/c001.xhtml")).readBytes().decodeToString() }
        assertTrue(body.contains("| coluna | valor |"))
    }

    @Test
    fun `TXT em Windows-1252 e decodificado`() {
        val bytes = "Capítulo 1\nAção e emoção.".toByteArray(charset("windows-1252"))
        val (_, epub) = convert(TextSource("antigo.txt", bytes))
        val body = ZipFile(epub).use { it.getInputStream(it.getEntry("OEBPS/c001.xhtml")).readBytes().decodeToString() }
        assertTrue(body.contains("Ação e emoção."))
    }

    @Test
    fun `capitulo enorme e dividido em partes`() {
        val huge = (1..4000).joinToString("\n\n") { "Parágrafo número $it com algum texto para ocupar espaço." }
        val (_, epub) = convert(txt("Longo.txt", huge))
        assertValidEpub(epub)
        val titles = chapterTitles(epub)
        assertTrue("esperava mais de uma parte, veio $titles", titles.size > 1)
        assertEquals("Longo (2)", titles[1])
    }
}
