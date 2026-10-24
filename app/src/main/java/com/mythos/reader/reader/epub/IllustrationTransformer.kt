package com.mythos.reader.reader.epub

import android.graphics.BitmapFactory
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map

/**
 * Exibe as ilustrações de um EPUB em tela cheia: cada uma ocupa uma página inteira, sem as
 * margens do texto. Os capítulos são reescritos ao serem servidos ao navegador, antes da
 * paginação, então a posição de leitura não salta.
 *
 * Só imagens grandes contam como ilustração; ícones, vinhetas e divisórias ficam como estão.
 *
 * O espaço vertical nativo do Readium em volta da página foi zerado (res/values/dimens.xml),
 * então todo capítulo recebe aqui a margem de cima e de baixo do texto, em CSS.
 */
fun Container<Resource>.withFullBleedIllustrations(): Container<Resource> {
    val images = this
    return TransformingContainer(this) { url, resource ->
        if (!url.isHtml()) return@TransformingContainer resource
        resource.map { bytes -> Try.success(transformHtml(url, bytes.decodeToString(), images).encodeToByteArray()) }
    }
}

private fun Url.isHtml(): Boolean {
    val path = path?.lowercase() ?: return false
    return path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm")
}

private suspend fun transformHtml(documentUrl: Url, html: String, images: Container<Resource>): String {
    // O Readium aplica onCreatePublication mais de uma vez; não transforma de novo.
    if (STYLE_MARKER in html) return html
    var marked = false

    val withImages = IMG_TAG.replaceSuspending(html) { match ->
        val src = SRC_ATTR.find(match.value)?.groupValues?.get(1)
        if (src != null && isIllustration(documentUrl, src, images)) {
            marked = true
            match.value.addClass(ILLUSTRATION_CLASS)
        } else {
            match.value
        }
    }

    // Capas e páginas de ilustração em SVG: <svg ...><image href="..."/></svg>
    val withSvg = SVG_WITH_IMAGE.replaceSuspending(withImages) { match ->
        val href = HREF_ATTR.find(match.groupValues[2])?.groupValues?.get(1)
        if (href != null && isIllustration(documentUrl, href, images)) {
            marked = true
            // "none" distorce a imagem quando a página não tem a mesma proporção.
            val svgTag = match.groupValues[1].addClass(ILLUSTRATION_CLASS)
                .replace(PRESERVE_NONE, "preserveAspectRatio=\"xMidYMid meet\"")
            svgTag + match.value.removePrefix(match.groupValues[1])
        } else {
            match.value
        }
    }

    val style = if (marked) PAGE_FRAME_STYLE + ILLUSTRATION_STYLE else PAGE_FRAME_STYLE
    val headEnd = withSvg.indexOf("</head>", ignoreCase = true)
    return if (headEnd >= 0) withSvg.replaceRange(headEnd, headEnd, style) else withSvg
}

private suspend fun isIllustration(documentUrl: Url, src: String, images: Container<Resource>): Boolean {
    val relative = Url(src.trim()) ?: return false
    val target = documentUrl.resolve(relative).normalize()
    val image = images[target] ?: return false

    // O cabeçalho basta para obter as dimensões de JPEG, PNG, GIF e WebP.
    val header = image.read(0L until HEADER_BYTES).getOrElse { return false }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(header, 0, header.size, options)
    val width = options.outWidth
    val height = options.outHeight

    if (width <= 0 || height <= 0) {
        // Formato não reconhecido pelo cabeçalho: decide pelo tamanho do arquivo.
        return image.length().getOrElse { return false } >= MIN_BYTES_WITHOUT_DIMENSIONS
    }
    val aspect = width.toDouble() / height
    // Faixas largas (títulos, divisórias) não são ilustrações de página inteira.
    return minOf(width, height) >= MIN_SIDE_PX && aspect in MIN_ASPECT..MAX_ASPECT
}

private fun String.addClass(name: String): String {
    val existing = CLASS_ATTR.find(this)
    return if (existing != null) {
        replaceRange(existing.groups[1]!!.range, "${existing.groupValues[1]} $name")
    } else {
        replaceFirst(TAG_NAME, "$0 class=\"$name\"")
    }
}

private suspend fun Regex.replaceSuspending(input: String, transform: suspend (MatchResult) -> String): String {
    val result = StringBuilder()
    var last = 0
    for (match in findAll(input)) {
        result.append(input, last, match.range.first).append(transform(match))
        last = match.range.last + 1
    }
    return result.append(input, last, input.length).toString()
}

private const val ILLUSTRATION_CLASS = "mythos-illustration"
private const val HEADER_BYTES = 64L * 1024
private const val MIN_BYTES_WITHOUT_DIMENSIONS = 40L * 1024
private const val MIN_SIDE_PX = 400
private const val MIN_ASPECT = 0.4
private const val MAX_ASPECT = 2.0

private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val SVG_WITH_IMAGE = Regex("(<svg\\b[^>]*>)\\s*(<image\\b[^>]*>)", RegexOption.IGNORE_CASE)
private val SRC_ATTR = Regex("\\ssrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val HREF_ATTR = Regex("\\s(?:xlink:)?href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val CLASS_ATTR = Regex("\\sclass\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
private val PRESERVE_NONE = Regex("preserveAspectRatio\\s*=\\s*[\"']none[\"']", RegexOption.IGNORE_CASE)
private val TAG_NAME = Regex("^<[a-zA-Z]+")

/**
 * Margem vertical de toda página de texto. O :root é o contêiner multicoluna do Readium e cada
 * coluna é uma página, então um padding nele vale para todas as páginas. `env(safe-area-inset-top)`
 * desvia da câmera e acompanha o dobrar/desdobrar sem código no app. `html:root` vence o
 * `:root { padding: 0 !important }` do Readium CSS por especificidade.
 */
private const val STYLE_MARKER = "mythos-page-frame"

/*
 * Margens de cima e de baixo do texto e, quando o app pede duas colunas (livro aberto), as
 * colunas em si: o Readium CSS só as aceita a partir de 960 dp, e a tela interna do Fold
 * deitada tem menos que isso. Como o app sempre envia uma ou duas colunas explicitamente
 * (nunca "automático"), a regra vale só quando ele decidiu pelo livro aberto.
 */

private val PAGE_FRAME_STYLE = """
<style type="text/css" id="$STYLE_MARKER">
:root { --mythos-pad-top: calc(env(safe-area-inset-top, 0px) + 40px); --mythos-pad-bottom: calc(env(safe-area-inset-bottom, 0px) + 40px); }
html:root { padding-top: var(--mythos-pad-top) !important; padding-bottom: var(--mythos-pad-bottom) !important; }
:root[style*="--USER__colCount: 2"], :root[style*="--USER__colCount:2"] {
  --RS__colWidth: auto !important;
  --RS__colCount: 2 !important;
  -webkit-column-count: 2 !important;
  column-count: 2 !important;
}
</style>
""".trimIndent()

/**
 * A página tem a largura da tela, ou metade dela no livro aberto (duas colunas). A ilustração sai do padding do <body> com o truque
 * de full-bleed `margin-left: calc(50% - largura / 2)` e sobe sobre a margem de cima com
 * `position: relative` — um `margin-top` negativo seria descartado pelo Chromium, que trunca
 * margens junto de quebras de coluna. Como é um bloco indivisível de 100vh, ocupa a página inteira.
 */
private val ILLUSTRATION_STYLE = """
<style type="text/css">
:root { --mythos-page-width: 100vw; }
:root[style*="--USER__colCount: 2"], :root[style*="--USER__colCount:2"] { --mythos-page-width: 50vw; }
.$ILLUSTRATION_CLASS {
  display: block !important;
  -webkit-column-break-before: always; break-before: column !important;
  -webkit-column-break-after: always; break-after: column !important;
  break-inside: avoid !important;
  box-sizing: border-box !important;
  width: var(--mythos-page-width) !important;
  max-width: none !important;
  height: 100vh !important;
  max-height: none !important;
  margin: 0 0 0 calc(50% - var(--mythos-page-width) / 2) !important;
  position: relative !important;
  top: calc(-1 * var(--mythos-pad-top)) !important;
  padding: 0 !important;
  border: 0 !important;
  object-fit: contain !important;
}
</style>
""".trimIndent()
