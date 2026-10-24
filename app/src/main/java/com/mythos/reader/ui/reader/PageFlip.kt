package com.mythos.reader.ui.reader

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * Virada de página como uma folha de papel.
 *
 * O Readium só sabe virar com ou sem deslizamento, então a folha é encenada por cima dele:
 * fotografa a página atual, cobre o leitor com a foto, avança sem animação, fotografa a nova
 * página e anima as duas fotos. Posição, destaques e progresso seguem nas mãos do Readium.
 *
 * Em [spread] (livro aberto) a folha gira em torno da lombada: a frente é a página direita
 * atual e o verso, a página esquerda seguinte, como num livro de verdade.
 */
class PageFlipController(
    private val scope: CoroutineScope,
    private val navigator: () -> EpubNavigatorFragment?,
) {
    var spread by mutableStateOf(false)

    /** Foto da página de antes da virada; enquanto houver, a cobertura está na tela. */
    var before by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Foto da página de destino; nula enquanto o Readium ainda não a desenhou. */
    var after by mutableStateOf<ImageBitmap?>(null)
        private set

    var forward by mutableStateOf(true)
        private set

    /** 0 = folha deitada na posição de origem; 1 = virada por completo. */
    var progress by mutableFloatStateOf(0f)
        private set

    val busy: Boolean get() = before != null

    // Reaproveitadas entre viradas: cada foto da tela interna ocupa cerca de 18 MB.
    private var beforeBuffer: Bitmap? = null
    private var afterBuffer: Bitmap? = null

    private var prepared: Deferred<Boolean>? = null
    private var dragWidth = 1f

    /** Virada completa e automática (toque na borda). */
    fun turn(forward: Boolean) {
        if (busy) return
        scope.launch {
            if (!prepare(forward)) return@launch
            animateTo(1f, TURN_MS)
            finish()
        }
    }

    /** Início de um arraste horizontal: a folha passa a acompanhar o dedo. */
    fun startDrag(forward: Boolean, width: Float) {
        if (busy) return
        dragWidth = width.coerceAtLeast(1f)
        val job = scope.async { prepare(forward) }
        prepared = job
    }

    /** [dx] é o deslocamento total do dedo desde o toque inicial. */
    fun drag(dx: Float) {
        if (prepared == null) return
        val towards = if (forward) -dx else dx
        progress = (towards / dragWidth).coerceIn(0f, 1f)
    }

    /** Fim do arraste: completa a virada ou devolve a folha, conforme distância e velocidade. */
    fun release(velocityX: Float) {
        val job = prepared ?: return
        prepared = null
        scope.launch {
            if (!job.await()) return@launch
            val towards = if (forward) -velocityX else velocityX
            val commit = towards > FLING_VELOCITY || (towards > -FLING_VELOCITY && progress > COMMIT_PROGRESS)
            if (commit) {
                animateTo(1f, (TURN_MS * (1f - progress)).toInt().coerceAtLeast(MIN_SETTLE_MS))
            } else {
                animateTo(0f, (TURN_MS * progress).toInt().coerceAtLeast(MIN_SETTLE_MS))
                // A cobertura continua mostrando a página original enquanto o Readium volta a ela.
                navigateAndSettle(!forward)
            }
            finish()
        }
    }

    /** Fotografa, cobre, navega e fotografa de novo. Falso se não houver para onde ir. */
    private suspend fun prepare(forward: Boolean): Boolean {
        val view = navigator()?.view?.takeIf { it.width > 0 && it.height > 0 } ?: return false
        this.forward = forward
        progress = 0f
        after = null
        beforeBuffer = capture(view, beforeBuffer)
        before = beforeBuffer!!.asImageBitmap()
        // Garante que a cobertura já foi desenhada antes de o Readium trocar a página por baixo.
        awaitFrames(1)
        if (!navigateAndSettle(forward)) {
            finish()
            return false
        }
        afterBuffer = capture(view, afterBuffer)
        after = afterBuffer!!.asImageBitmap()
        return true
    }

    private suspend fun navigateAndSettle(forward: Boolean): Boolean {
        val nav = navigator() ?: return false
        val previous = nav.currentLocator.value
        val moved = if (forward) nav.goForward(animated = false) else nav.goBackward(animated = false)
        if (!moved) return false
        // A rolagem do Readium é feita em JavaScript: espera a nova posição ser informada
        // e mais alguns quadros para o WebView desenhá-la.
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) { nav.currentLocator.first { it != previous } }
        awaitFrames(2)
        return true
    }

    private suspend fun animateTo(target: Float, durationMs: Int) {
        animate(progress, target, animationSpec = tween(durationMs, easing = FastOutSlowInEasing)) { value, _ ->
            progress = value
        }
    }

    private fun finish() {
        before = null
        after = null
        progress = 0f
    }

    /** Libera as fotos guardadas (ao sair do leitor). */
    fun dispose() {
        finish()
        beforeBuffer = null
        afterBuffer = null
    }

    private fun capture(view: View, reuse: Bitmap?): Bitmap {
        val bitmap = reuse?.takeIf { it.width == view.width && it.height == view.height }
            ?: Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private suspend fun awaitFrames(count: Int) = repeat(count) { withFrameNanos { } }

    private companion object {
        const val TURN_MS = 520
        const val MIN_SETTLE_MS = 160
        const val SETTLE_TIMEOUT_MS = 800L
        const val COMMIT_PROGRESS = 0.3f
        /** px/s; um gesto rápido vira a página mesmo que curto. */
        const val FLING_VELOCITY = 1200f
    }
}

/**
 * Captura arrastes horizontais antes do WebView para que a folha siga o dedo. Toques, arrastes
 * verticais e pressionar-e-arrastar (seleção de texto) continuam com o Readium.
 */
fun Modifier.pageFlipGestures(controller: PageFlipController, enabled: () -> Boolean): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!enabled()) return@awaitEachGesture
            if (controller.busy) {
                // Durante a virada o leitor por baixo já mostra outra página: nada chega a ele.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                return@awaitEachGesture
            }
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) {
                    if (dragging) controller.release(0f)
                    return@awaitEachGesture
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                velocity.addPosition(change.uptimeMillis, change.position)
                val delta = change.position - down.position
                if (!change.pressed) {
                    if (dragging) {
                        change.consume()
                        controller.release(velocity.calculateVelocity().x)
                    }
                    return@awaitEachGesture
                }
                if (!dragging) {
                    // Segurar antes de mover é seleção de texto; não é virada de página.
                    if (change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                        return@awaitEachGesture
                    }
                    val slop = viewConfiguration.touchSlop
                    if (abs(delta.y) > slop && abs(delta.y) > abs(delta.x)) return@awaitEachGesture
                    if (abs(delta.x) > slop && abs(delta.x) > abs(delta.y) * 1.5f) {
                        dragging = true
                        controller.startDrag(forward = delta.x < 0, width = size.width.toFloat())
                    }
                }
                if (dragging) {
                    // Consumir faz o Compose enviar ACTION_CANCEL ao WebView, que desiste do gesto.
                    change.consume()
                    controller.drag(delta.x)
                }
            }
        }
    }

/** Cobertura que encena a folha virando. Só existe durante a virada. */
@Composable
fun PageFlipOverlay(controller: PageFlipController, modifier: Modifier = Modifier) {
    val before = controller.before ?: return
    val after = controller.after
    Box(modifier.fillMaxSize()) {
        if (after == null) {
            Canvas(Modifier.fillMaxSize()) { drawPage(before, 0f, 1f) }
            return@Box
        }
        val progress = controller.progress
        if (controller.spread) {
            SpreadFlip(before, after, progress, controller.forward)
        } else {
            SingleFlip(before, after, progress, controller.forward)
        }
    }
}

/**
 * Livro aberto. Avançando, a folha da direita levanta da lombada (frente = direita atual),
 * passa pela vertical e deita à esquerda mostrando o verso (= esquerda seguinte).
 * Voltando, o mesmo movimento espelhado.
 */
@Composable
private fun SpreadFlip(before: ImageBitmap, after: ImageBitmap, progress: Float, forward: Boolean) {
    // Metade de origem da folha: direita ao avançar, esquerda ao voltar.
    val leafFrom = if (forward) 0.5f else 0f
    val leafTo = 0.5f - leafFrom
    val firstHalf = progress < 0.5f
    // 0° deitada, 90° em pé sobre a lombada.
    val lift = if (firstHalf) progress * 2f else (1f - progress) * 2f

    Canvas(Modifier.fillMaxSize()) {
        // Fundo: o lado de onde a folha sai já mostra a página nova; o outro, a antiga até ser coberto.
        drawPage(after, leafFrom, leafFrom + 0.5f)
        drawPage(before, leafTo, leafTo + 0.5f)
        // Sombra da folha levantada sobre a página revelada, junto à lombada.
        drawSpineShadow(sideStart = if (firstHalf) leafFrom else leafTo, lift = lift)
    }

    val face = if (firstHalf) before else after
    val faceStart = if (firstHalf) leafFrom else leafTo
    // A folha gira sobre a lombada: a borda presa fica no centro da tela.
    val pivotX = if (faceStart == 0.5f) 0f else 1f
    // Sinal escolhido para a borda solta vir na direção de quem lê, e não afundar na tela.
    val direction = if (faceStart == 0.5f) -1f else 1f
    Leaf(
        page = face,
        start = faceStart,
        rotationY = direction * lift * 90f,
        pivotX = pivotX,
        shade = lift,
    )
}

/** Uma página só: a folha gira presa à borda esquerda, como num caderno. */
@Composable
private fun SingleFlip(before: ImageBitmap, after: ImageBitmap, progress: Float, forward: Boolean) {
    // Avançando, a página atual sai girando; voltando, a anterior entra girando por cima.
    val under = if (forward) after else before
    val leaf = if (forward) before else after
    val lift = if (forward) progress else 1f - progress
    Canvas(Modifier.fillMaxSize()) { drawPage(under, 0f, 1f) }
    // Para dentro da tela: girando em direção ao leitor, a perspectiva ampliaria a página até
    // cobrir a seguinte; assim ela recua para a borda e revela a próxima aos poucos.
    Leaf(page = leaf, start = 0f, width = 1f, rotationY = lift * 90f, pivotX = 0f, shade = lift)
}

/**
 * Metade (ou página inteira) da foto, girada em 3D. [start] e [width] são frações da largura;
 * [shade] de 0 a 1 escurece a face conforme ela se afasta da luz.
 */
@Composable
private fun Leaf(page: ImageBitmap, start: Float, rotationY: Float, pivotX: Float, shade: Float, width: Float = 0.5f) {
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.rotationY = rotationY
                // Perspectiva suave: com a distância padrão a folha "explode" na direção da câmera.
                cameraDistance = 24f * density
                transformOrigin = TransformOrigin(start + pivotX * width, 0.5f)
            },
    ) {
        drawPage(page, start, start + width)
        val left = size.width * start
        val pageWidth = size.width * width
        // Escurece mais longe da lombada, onde a folha se curva em direção à luz.
        val hinge = if (pivotX == 0f) left else left + pageWidth
        val far = if (pivotX == 0f) left + pageWidth else left
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.10f * shade),
                1f to Color.Black.copy(alpha = 0.38f * shade),
                startX = hinge,
                endX = far,
            ),
            topLeft = Offset(left, 0f),
            size = Size(pageWidth, size.height),
        )
    }
}

/** Desenha a fatia [from, to) da foto (em frações da largura) na mesma posição da tela. */
private fun DrawScope.drawPage(page: ImageBitmap, from: Float, to: Float) {
    val srcX = (page.width * from).roundToInt()
    val srcW = (page.width * to).roundToInt() - srcX
    val dstX = (size.width * from).roundToInt()
    val dstW = (size.width * to).roundToInt() - dstX
    drawImage(
        page,
        srcOffset = IntOffset(srcX, 0),
        srcSize = IntSize(srcW, page.height),
        dstOffset = IntOffset(dstX, 0),
        dstSize = IntSize(dstW, size.height.roundToInt()),
    )
}

/**
 * Sombra junto à lombada sobre a metade que começa em [sideStart]. Mais larga e escura
 * quanto mais a folha estiver levantada ([lift]).
 */
private fun DrawScope.drawSpineShadow(sideStart: Float, lift: Float) {
    if (lift <= 0f) return
    val spine = size.width / 2f
    val reach = size.width / 2f * 0.35f * sin(lift * PI.toFloat() / 2f)
    val alpha = 0.30f * cos((1f - lift) * PI.toFloat() / 2f)
    val towardsRight = sideStart == 0.5f
    val end = if (towardsRight) spine + reach else spine - reach
    clipRect(left = minOf(spine, end), right = maxOf(spine, end)) {
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = alpha),
                1f to Color.Transparent,
                startX = spine,
                endX = end,
            ),
        )
    }
}

/**
 * Lombada do livro aberto: sombra suave no vinco central e um leve escurecimento nas bordas,
 * para que as duas colunas pareçam páginas. Não intercepta toques.
 */
@Composable
fun BookSpine(dark: Boolean, modifier: Modifier = Modifier) {
    val strength = if (dark) 0.35f else 0.16f
    Canvas(modifier.fillMaxSize()) {
        val spine = size.width / 2f
        val fold = size.width * 0.045f
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                0.7f to Color.Black.copy(alpha = strength * 0.45f),
                1f to Color.Black.copy(alpha = strength),
                startX = spine - fold,
                endX = spine,
            ),
            topLeft = Offset(spine - fold, 0f),
            size = Size(fold, size.height),
        )
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = strength),
                0.3f to Color.Black.copy(alpha = strength * 0.45f),
                1f to Color.Transparent,
                startX = spine,
                endX = spine + fold,
            ),
            topLeft = Offset(spine, 0f),
            size = Size(fold, size.height),
        )
        // Bordas externas: o papel "desce" levemente em direção às margens.
        val edge = size.width * 0.02f
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = strength * 0.4f),
                1f to Color.Transparent,
                startX = 0f,
                endX = edge,
            ),
            size = Size(edge, size.height),
        )
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = strength * 0.4f),
                startX = size.width - edge,
                endX = size.width,
            ),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
        )
    }
}
