package com.mythos.reader.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mythos.reader.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Deslocamentos de poucos pixels que evitam marcar a tela OLED com uma imagem parada. */
private val BurnInOffsets = listOf(0 to 0, 2 to 1, 0 to 2, -2 to 1, -1 to -2, 1 to -1)
private const val BURN_IN_SHIFT_MS = 60_000L

/**
 * Conteúdo da tela externa no Duo performático: a capa do livro aberto, como um livro físico
 * visto de fora. O fundo preto apaga os pixels da tela OLED e quase não consome bateria.
 */
@Composable
fun DuoCover(book: Book, progression: Double) {
    val cover by produceState<ImageBitmap?>(null, book.coverPath) {
        value = book.coverPath?.let { path ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
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

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp).offset(dx.dp, dy.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val shape = RoundedCornerShape(8.dp)
            val image = cover
            if (image != null) {
                Image(
                    image,
                    contentDescription = book.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f, fill = false).clip(shape),
                )
            } else {
                // Sem capa no EPUB: uma "capa" tipográfica no lugar.
                Box(
                    Modifier.fillMaxWidth(0.7f).aspectRatio(2f / 3f).clip(shape).background(Color(0xFF1E1E1E)),
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (image != null) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFCFC6B8),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                book.author?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8A8A8A),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${(progression * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6E6E6E),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
