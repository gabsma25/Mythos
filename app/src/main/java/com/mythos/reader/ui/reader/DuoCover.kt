package com.mythos.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mythos.reader.model.Book
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Deslocamentos de poucos pixels que evitam marcar a tela OLED com uma imagem parada. */
private val BurnInOffsets = listOf(0 to 0, 2 to 1, 0 to 2, -2 to 1, -1 to -2, 1 to -1)
private const val BURN_IN_SHIFT_MS = 60_000L

/** Ampliação mínima que esconde as bordas quando a capa se desloca contra marcas na tela. */
private const val BURN_IN_SCALE = 1.01f

/** Diferença de proporção até a qual cortar a capa só perde uma margem; acima disso cortaria o título. */
private const val CROP_TOLERANCE = 0.04f

/**
 * Conteúdo da tela externa no Duo performático: a capa do livro aberto ocupando a tela inteira,
 * como um livro físico visto de fora.
 *
 * @param loadCover capa em resolução suficiente para [Size]; a miniatura guardada pela
 * biblioteca (480 × 720) fica borrada esticada na tela externa e serve só de reserva.
 */
@Composable
fun DuoCover(book: Book, progression: Double, loadCover: suspend (Size) -> Bitmap?) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        // A capa costuma ser retrato, como a tela; pedir o lado maior nos dois sentidos garante
        // resolução para preencher a tela sem ampliar a imagem.
        val longest = with(density) { maxOf(maxWidth, maxHeight).toPx() }.roundToInt()
        val cover by produceState<ImageBitmap?>(null, book.id, longest) {
            value = withContext(Dispatchers.IO) {
                runCatching { loadCover(Size(longest, longest)) }.getOrNull()
                    ?: book.coverPath?.let(BitmapFactory::decodeFile)
            }?.asImageBitmap()
        }

        // Uma mudança por minuto: custo desprezível, sem animação permanente.
        var shift by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(BURN_IN_SHIFT_MS)
                shift = (shift + 1) % BurnInOffsets.size
            }
        }
        val (dx, dy) = BurnInOffsets[shift]
        val burnIn = Modifier.graphicsLayer {
            translationX = dx.dp.toPx()
            translationY = dy.dp.toPx()
            scaleX = BURN_IN_SCALE
            scaleY = BURN_IN_SCALE
        }

        val image = cover
        // A capa aparece sempre inteira: só é cortada quando tem quase a proporção da tela.
        // A sobra (laterais com a tela deitada, faixas finas com ela em pé) é preenchida pela
        // própria capa ampliada e desfocada, para a tela não ganhar faixas pretas.
        val screenAspect = maxWidth / maxHeight
        val fillsScreen = image != null &&
            abs(screenAspect / (image.width.toFloat() / image.height) - 1f) <= CROP_TOLERANCE
        if (image != null && fillsScreen) {
            Image(
                image,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(burnIn),
            )
        } else if (image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.55f), BlendMode.SrcAtop),
                modifier = Modifier.fillMaxSize().blur(32.dp),
            )
            Image(
                image,
                contentDescription = book.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().then(burnIn),
            )
        } else {
            // Sem capa no EPUB: uma "capa" tipográfica no lugar.
            Box(
                Modifier.align(Alignment.Center).fillMaxHeight(0.7f).aspectRatio(2f / 3f).then(burnIn)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFCFC6B8),
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }

        // Progresso discreto sobre um esmaecimento no pé da capa, para não brigar com a arte.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${(progression * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFBDBDBD),
                modifier = Modifier.padding(top = 32.dp, bottom = 20.dp).then(burnIn),
            )
        }
    }
}
