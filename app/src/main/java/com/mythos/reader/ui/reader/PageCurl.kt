package com.mythos.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Folha de papel que se curva ao virar.
 *
 * A folha é uma tira de comprimento fixo presa à lombada. Cada ponto a uma distância `u` da
 * lombada (0 = lombada, 1 = borda solta) tem o seu próprio ângulo, e a forma sai da integral
 * desses ângulos: como papel, a folha dobra mas nunca estica. A borda solta vai à frente do
 * resto, como quando se puxa a página pela ponta, e é isso que desenha a curva.
 *
 * As fotos das páginas são esticadas sobre a curva com [Canvas.drawBitmapMesh], com
 * perspectiva e sombreamento por faixa.
 */
internal class PageCurl {

    // Amostras ao longo da folha, da lombada à borda solta, em frações da largura da folha.
    private val angle = FloatArray(SEGMENTS + 1)
    private val along = FloatArray(SEGMENTS + 1)
    private val lift = FloatArray(SEGMENTS + 1)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint()
    private val srcRect = Rect()
    private val dstRect = RectF()

    private var verts = FloatArray(0)
    private var colors = IntArray(0)

    // Verso de uma página única: papel liso na cor do fundo do leitor.
    private var paper: Bitmap? = null
    private var paperColor = 0

    /**
     * Páginas que ficam por baixo da folha. A lombada é desenhada entre esta etapa e
     * [drawTurningLeaf], para ficar sob a folha que vira e não por cima dela.
     */
    fun drawPagesUnder(
        canvas: Canvas,
        width: Float,
        height: Float,
        before: Bitmap,
        after: Bitmap,
        spread: Boolean,
        forward: Boolean,
    ) {
        if (spread) {
            // O lado de onde a folha sai já mostra a página nova; o outro, a antiga até ser coberto.
            drawSlice(canvas, after, if (forward) 0.5f else 0f, width, height)
            drawSlice(canvas, before, if (forward) 0f else 0.5f, width, height)
        } else {
            // Voltando, a página anterior desce de volta por cima da atual.
            dstRect.set(0f, 0f, width, height)
            canvas.drawBitmap(if (forward) after else before, null, dstRect, bitmapPaint)
        }
    }

    /** A folha que vira e a sombra dela. [progress] vai de 0 (antes) a 1 (virada concluída). */
    fun drawTurningLeaf(
        canvas: Canvas,
        width: Float,
        height: Float,
        before: Bitmap,
        after: Bitmap,
        progress: Float,
        spread: Boolean,
        forward: Boolean,
        paperColor: Int,
    ) {
        val half = width / 2f
        if (spread) {
            // Avançando, a folha sai da direita (frente = direita atual, verso = esquerda
            // seguinte); voltando, sai da esquerda.
            val dir = if (forward) 1f else -1f
            shape(progress, returning = false)
            drawLeaf(
                canvas, width, height,
                hingeX = half, dir = dir, leafWidth = half,
                front = before, frontStart = half, frontDir = dir,
                back = after, backStart = half, backDir = -dir,
            )
        } else {
            // Presa à borda esquerda.
            shape(progress, returning = !forward)
            drawLeaf(
                canvas, width, height,
                hingeX = 0f, dir = 1f, leafWidth = width,
                front = if (forward) before else after, frontStart = 0f, frontDir = 1f,
                back = paperBitmap(paperColor), backStart = 0f, backDir = 1f,
            )
        }
    }

    /** Posição na tela da borda solta da folha em [progress] (sem a perspectiva, que é mínima). */
    fun edgeX(progress: Float, width: Float, spread: Boolean, forward: Boolean): Float {
        shape(progress, returning = !spread && !forward)
        return if (spread) {
            width / 2f + (if (forward) 1f else -1f) * along[SEGMENTS] * width / 2f
        } else {
            along[SEGMENTS] * width
        }
    }

    /**
     * Progresso em que a borda solta fica em [targetX], para que ela acompanhe o dedo. A borda
     * anda sempre no mesmo sentido ao longo da virada, então uma bisseção basta.
     */
    fun progressForEdge(targetX: Float, width: Float, spread: Boolean, forward: Boolean): Float {
        val start = edgeX(0f, width, spread, forward)
        val end = edgeX(1f, width, spread, forward)
        val wanted = ((targetX - start) / (end - start)).coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(BISECTION_STEPS) {
            val mid = (low + high) / 2f
            val reached = (edgeX(mid, width, spread, forward) - start) / (end - start)
            if (reached < wanted) low = mid else high = mid
        }
        return (low + high) / 2f
    }

    /**
     * Ângulo de cada ponto da folha. Virando, vai de 0 (deitada) a π (deitada do outro lado);
     * voltando ([returning]), o caminho inverso. Nos dois casos a borda solta lidera.
     */
    private fun shape(progress: Float, returning: Boolean) {
        for (k in 0..SEGMENTS) {
            val u = k / SEGMENTS.toFloat()
            var f = (progress * (1f + LEAD) - LEAD * (1f - u)).coerceIn(0f, 1f)
            f = f * f * (3f - 2f * f)
            angle[k] = PI.toFloat() * if (returning) 1f - f else f
        }
        along[0] = 0f
        lift[0] = 0f
        for (k in 1..SEGMENTS) {
            val mid = (angle[k - 1] + angle[k]) / 2f
            along[k] = along[k - 1] + cos(mid) / SEGMENTS
            lift[k] = lift[k - 1] + sin(mid) / SEGMENTS
        }
    }

    /**
     * Folha a partir de [hingeX], estendendo-se no sentido [dir]. A frente é a fatia de [front]
     * que começa em [frontStart] e avança no sentido [frontDir]; o mesmo para o verso.
     */
    private fun drawLeaf(
        canvas: Canvas,
        width: Float,
        height: Float,
        hingeX: Float,
        dir: Float,
        leafWidth: Float,
        front: Bitmap,
        frontStart: Float,
        frontDir: Float,
        back: Bitmap,
        backStart: Float,
        backDir: Float,
    ) {
        drawCastShadow(canvas, width, height, hingeX, dir, leafWidth)
        // Frente onde a folha ainda olha para o leitor (até 90°); verso depois disso.
        drawFace(canvas, width, height, hingeX, dir, leafWidth, front, frontStart, frontDir, isFront = true)
        drawFace(canvas, width, height, hingeX, dir, leafWidth, back, backStart, backDir, isFront = false)
    }

    private fun drawFace(
        canvas: Canvas,
        width: Float,
        height: Float,
        hingeX: Float,
        dir: Float,
        leafWidth: Float,
        bitmap: Bitmap,
        sliceStart: Float,
        sliceDir: Float,
        isFront: Boolean,
    ) {
        val (first, last) = visibleRange(isFront) ?: return

        // A malha cobre o bitmap inteiro; colunas fora da folha ou da face visível são
        // achatadas na borda mais próxima e viram triângulos sem área.
        val scale = bitmap.width / width
        val sliceWidth = leafWidth * scale
        val columns = (SEGMENTS * bitmap.width / sliceWidth).roundToInt().coerceAtLeast(1)
        val vertexCount = (columns + 1) * (ROWS + 1)
        if (verts.size < vertexCount * 2) {
            verts = FloatArray(vertexCount * 2)
            colors = IntArray(vertexCount)
        }
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0..columns) {
            val srcX = bitmap.width * i / columns.toFloat()
            val u = ((srcX - sliceStart * scale) * sliceDir / sliceWidth).coerceIn(0f, 1f)
            val k = (u * SEGMENTS).roundToInt().coerceIn(first, last)
            val x = hingeX + dir * along[k] * leafWidth
            val perspective = perspective(k)
            val screenX = cx + (x - cx) * perspective
            // Faixas inclinadas recebem menos luz; a face de trás, ainda menos.
            val facing = abs(cos(angle[k]))
            val light = (if (isFront) 0.58f else 0.5f) + 0.42f * facing
            val level = (light * 255).roundToInt().coerceIn(0, 255)
            val color = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
            for (row in 0..ROWS) {
                val index = row * (columns + 1) + i
                val y = height * row / ROWS.toFloat()
                verts[index * 2] = screenX
                verts[index * 2 + 1] = cy + (y - cy) * perspective
                colors[index] = color
            }
        }
        canvas.drawBitmapMesh(bitmap, columns, ROWS, verts, 0, colors, 0, bitmapPaint)
    }

    /**
     * Trecho da folha que mostra a face pedida. Inclui uma amostra além da dobra para que
     * frente e verso se encontrem sem uma fresta entre eles.
     */
    private fun visibleRange(isFront: Boolean): Pair<Int, Int>? {
        var first = -1
        var last = -1
        for (k in 0..SEGMENTS) {
            val front = angle[k] <= PI.toFloat() / 2f
            if (front == isFront) {
                if (first < 0) first = k
                last = k
            }
        }
        if (first < 0) return null
        return (first - 1).coerceAtLeast(0) to (last + 1).coerceAtMost(SEGMENTS)
    }

    /** Sombra que a folha levantada projeta sobre as páginas de baixo, junto às suas bordas. */
    private fun drawCastShadow(
        canvas: Canvas,
        width: Float,
        height: Float,
        hingeX: Float,
        dir: Float,
        leafWidth: Float,
    ) {
        var height3d = 0f
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        val cx = width / 2f
        for (k in 0..SEGMENTS) {
            val x = hingeX + dir * along[k] * leafWidth
            val screenX = cx + (x - cx) * perspective(k)
            minX = minOf(minX, screenX)
            maxX = maxOf(maxX, screenX)
            height3d = maxOf(height3d, lift[k])
        }
        if (height3d <= 0f) return
        val strength = (height3d * 1.6f).coerceAtMost(1f)
        val reach = leafWidth * 0.12f * strength
        val alpha = (0.32f * strength * 255).roundToInt()
        val shade = alpha shl 24
        if (maxX < width) {
            shadowPaint.shader = LinearGradient(maxX, 0f, maxX + reach, 0f, shade, 0, Shader.TileMode.CLAMP)
            canvas.drawRect(maxX, 0f, maxX + reach, height, shadowPaint)
        }
        if (minX > 0f) {
            shadowPaint.shader = LinearGradient(minX, 0f, minX - reach, 0f, shade, 0, Shader.TileMode.CLAMP)
            canvas.drawRect(minX - reach, 0f, minX, height, shadowPaint)
        }
    }

    /**
     * Quanto o ponto [k] parece maior por estar mais perto do leitor. Limitado de propósito:
     * a perspectiva de uma câmera de verdade fazia a folha levantada crescer e sair da tela.
     */
    private fun perspective(k: Int): Float = 1f + MAX_ZOOM * lift[k].coerceIn(0f, 1f)

    /** Metade da foto ([from] = 0 ou 0,5 da largura) na mesma posição da tela. */
    private fun drawSlice(canvas: Canvas, bitmap: Bitmap, from: Float, width: Float, height: Float) {
        val srcLeft = (bitmap.width * from).roundToInt()
        srcRect.set(srcLeft, 0, srcLeft + bitmap.width / 2, bitmap.height)
        dstRect.set(width * from, 0f, width * from + width / 2f, height)
        canvas.drawBitmap(bitmap, srcRect, dstRect, bitmapPaint)
    }

    private fun paperBitmap(color: Int): Bitmap {
        paper?.takeIf { paperColor == color }?.let { return it }
        return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.also {
            paper = it
            paperColor = color
        }
    }

    private companion object {
        /** Amostras ao longo da folha; mais que isso não muda nada visível. */
        const val SEGMENTS = 48
        /** Faixas na vertical: reduzem a distorção da perspectiva dentro de cada triângulo. */
        const val ROWS = 8
        /** Quanto a borda solta se adianta ao resto da folha; maior = curva mais acentuada. */
        const val LEAD = 0.45f
        const val BISECTION_STEPS = 20
        /** Aumento máximo da parte mais levantada da folha (4%). */
        const val MAX_ZOOM = 0.04f
    }
}
