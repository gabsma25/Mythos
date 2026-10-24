package com.mythos.reader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mythos.reader.appContainer
import com.mythos.reader.data.repository.locator
import com.mythos.reader.model.Highlight
import com.mythos.reader.ui.highlights.HighlightEditor
import com.mythos.reader.ui.highlights.HighlightItem
import com.mythos.reader.ui.highlights.copyToClipboard
import com.mythos.reader.model.ReaderPageTurn
import com.mythos.reader.reader.epub.isBookSpread
import com.mythos.reader.reader.epub.isDark
import com.mythos.reader.reader.epub.toEpubPreferences
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression

/** Mesmas faixas do [DirectionalNavigationAdapter]: 30% de cada borda, nunca menos que isto. */
private val MIN_TAP_EDGE = 80.dp
private const val TAP_EDGE_FRACTION = 0.3f

/** Toques nas bordas viram a página como folha; o centro segue para os próximos ouvintes. */
private fun flipTaps(nav: EpubNavigatorFragment, flip: PageFlipController, minEdgePx: Float) = object : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        val width = nav.view?.width?.toFloat() ?: return false
        val edge = maxOf(width * TAP_EDGE_FRACTION, minEdgePx)
        when {
            event.point.x < edge -> flip.turn(forward = false)
            event.point.x > width - edge -> flip.turn(forward = true)
            else -> return false
        }
        return true
    }
}

/**
 * Pede ao sistema que deixe as bordas laterais com o leitor. Com as barras ocultas, um arraste
 * que começa colado na borda era tratado como gesto do sistema e fazia a barra de status aparecer
 * no meio da leitura. Com as barras ocultas o Android aceita a borda inteira (fora disso, só
 * 200 dp por borda). Em troca, deslizar da lateral vira a página em vez de acionar o "voltar".
 */
private fun Modifier.readerEdgeGestureExclusion(stripPx: Float): Modifier = this
    .systemGestureExclusion { Rect(0f, 0f, stripPx, it.size.height.toFloat()) }
    .systemGestureExclusion {
        val width = it.size.width.toFloat()
        Rect(width - stripPx, 0f, width, it.size.height.toFloat())
    }

private val EDGE_EXCLUSION_WIDTH = 48.dp

private enum class ReaderSheet { NONE, SETTINGS, CONTENTS, HIGHLIGHTS }

private const val HIGHLIGHT_GROUP = "highlights"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val publication = viewModel.session.publication
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(ReaderSheet.NONE) }

    // A permissão é concedida fora do app; relê ao voltar das configurações.
    val context = LocalContext.current
    val focusMode = context.appContainer.readingFocusMode
    var focusPermissionGranted by remember { mutableStateOf(focusMode.hasPermission) }
    LifecycleResumeEffect(Unit) {
        focusPermissionGranted = focusMode.hasPermission
        onPauseOrDispose { }
    }

    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    // Destaque tocado no texto; abre o painel de cor/remoção.
    var editingHighlightId by rememberSaveable { mutableStateOf<Long?>(null) }

    val locator: Locator? by produceState(viewModel.session.lastLocator, navigator) {
        navigator?.currentLocator?.collect { value = it }
    }

    LaunchedEffect(navigator) {
        navigator?.currentLocator?.collect(viewModel::onLocatorChanged)
    }

    // Fold aberto na horizontal: duas páginas lado a lado, como um livro.
    val spread = preferences.isBookSpread(LocalConfiguration.current)

    LaunchedEffect(navigator, preferences, spread) {
        navigator?.submitPreferences(preferences.toEpubPreferences(spread))
    }

    val pageFlip = remember { PageFlipController(scope) { navigator } }
    SideEffect {
        pageFlip.spread = spread
        pageFlip.paperColor = preferences.backgroundColor
        pageFlip.darkPaper = preferences.isDark
    }
    DisposableEffect(Unit) { onDispose { pageFlip.dispose() } }
    // Livros da direita para a esquerda continuam com o deslizamento do Readium.
    val flipPages = preferences.pageTurn == ReaderPageTurn.FLIP &&
        navigator?.overflow?.value?.readingProgression != ReadingProgression.RTL
    val selecting by viewModel.selectionActive.collectAsStateWithLifecycle()
    val canDragFlip by rememberUpdatedState(flipPages && !selecting)
    val minEdgePx = with(LocalDensity.current) { MIN_TAP_EDGE.toPx() }
    val edgeStripPx = with(LocalDensity.current) { EDGE_EXCLUSION_WIDTH.toPx() }

    // O Readium compara com a lista anterior e redesenha só o que mudou.
    LaunchedEffect(navigator, highlights) {
        navigator?.applyDecorations(
            highlights.mapNotNull { highlight ->
                Decoration(
                    id = highlight.id.toString(),
                    locator = highlight.locator() ?: return@mapNotNull null,
                    style = Decoration.Style.Highlight(tint = highlight.color.argb.toInt()),
                )
            },
            HIGHLIGHT_GROUP,
        )
    }

    DisposableEffect(navigator) {
        val nav = navigator ?: return@DisposableEffect onDispose { }
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                editingHighlightId = event.decoration.id.toLongOrNull()
                controlsVisible = false
                return true
            }
        }
        nav.addDecorationListener(HIGHLIGHT_GROUP, listener)
        onDispose { nav.removeDecorationListener(listener) }
    }

    // Toques: bordas viram a página, o centro mostra ou oculta os controles.
    // Com os controles visíveis, qualquer toque no texto apenas os oculta.
    DisposableEffect(navigator, preferences.pageTurn, flipPages) {
        val nav = navigator ?: return@DisposableEffect onDispose { }
        val dismissControls = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                if (!controlsVisible) return false
                controlsVisible = false
                return true
            }
        }
        val pageTurns = if (flipPages) {
            flipTaps(nav, pageFlip, minEdgePx)
        } else {
            DirectionalNavigationAdapter(nav, animatedTransition = preferences.pageTurn == ReaderPageTurn.SLIDE)
        }
        val toggleControls = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                controlsVisible = true
                return true
            }
        }
        listOf(dismissControls, pageTurns, toggleControls).forEach(nav::addInputListener)
        onDispose {
            listOf(dismissControls, pageTurns, toggleControls).forEach(nav::removeInputListener)
        }
    }

    BackHandler(enabled = controlsVisible) { controlsVisible = false }
    // O menu de seleção do Readium é flutuante e não trata o Voltar: sem isto, ele fecharia o livro.
    BackHandler(enabled = selecting) { navigator?.clearSelection() }

    Box(Modifier.fillMaxSize().background(Color(preferences.backgroundColor))) {
        AndroidFragment<EpubNavigatorFragment>(
            Modifier.fillMaxSize()
                .readerEdgeGestureExclusion(edgeStripPx)
                .pageFlipGestures(pageFlip) { canDragFlip },
        ) { navigator = it }
        if (spread) BookSpine(dark = preferences.isDark)
        // Por cima da lombada: durante a virada, a cobertura desenha a própria lombada sob a
        // folha, para a sombra do vinco não cair sobre a página que está virando.
        PageFlipOverlay(pageFlip)

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(
                title = publication.metadata.title.orEmpty(),
                chapter = locator?.title,
                onBack = onBack,
                onContents = { sheet = ReaderSheet.CONTENTS },
                onHighlights = { sheet = ReaderSheet.HIGHLIGHTS },
                onSettings = { sheet = ReaderSheet.SETTINGS },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ReaderBottomBar(
                progression = locator?.locations?.totalProgression ?: 0.0,
                onSeek = { progression ->
                    scope.launch {
                        publication.locateProgression(progression)?.let { navigator?.go(it) }
                    }
                },
            )
        }
    }

    when (sheet) {
        ReaderSheet.NONE -> Unit
        ReaderSheet.SETTINGS -> ModalBottomSheet(onDismissRequest = { sheet = ReaderSheet.NONE }) {
            KeepSystemBarsHidden()
            ReaderSettings(
                preferences = preferences,
                onChange = viewModel::updatePreferences,
                focusPermissionGranted = focusPermissionGranted,
                onRequestFocusPermission = { context.startActivity(focusMode.permissionIntent()) },
            )
        }
        ReaderSheet.CONTENTS -> ModalBottomSheet(onDismissRequest = { sheet = ReaderSheet.NONE }) {
            KeepSystemBarsHidden()
            TableOfContents(
                links = remember(publication) { publication.tableOfContents.flatten() },
                onSelect = { link ->
                    navigator?.go(link)
                    sheet = ReaderSheet.NONE
                    controlsVisible = false
                },
            )
        }
        ReaderSheet.HIGHLIGHTS -> ModalBottomSheet(onDismissRequest = { sheet = ReaderSheet.NONE }) {
            KeepSystemBarsHidden()
            BookHighlightsList(
                highlights = highlights,
                onSelect = { highlight ->
                    highlight.locator()?.let { navigator?.go(it) }
                    sheet = ReaderSheet.NONE
                    controlsVisible = false
                },
            )
        }
    }

    highlights.firstOrNull { it.id == editingHighlightId }?.let { highlight ->
        ModalBottomSheet(onDismissRequest = { editingHighlightId = null }) {
            KeepSystemBarsHidden()
            HighlightEditor(
                highlight = highlight,
                onColor = { viewModel.setHighlightColor(highlight, it) },
                onCopy = {
                    context.copyToClipboard(publication.metadata.title.orEmpty(), highlight.text)
                    editingHighlightId = null
                },
                onDelete = {
                    viewModel.deleteHighlight(highlight)
                    editingHighlightId = null
                },
            )
        }
    }
}

@Composable
private fun BookHighlightsList(highlights: List<Highlight>, onSelect: (Highlight) -> Unit) {
    if (highlights.isEmpty()) {
        Text(
            "Nenhum destaque neste livro. Mantenha o dedo sobre uma palavra, ajuste a seleção e toque em Destacar.",
            Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        items(highlights, key = { it.id }) { highlight ->
            HighlightItem(highlight, onOpen = { onSelect(highlight) })
        }
    }
}

/**
 * O ModalBottomSheet abre numa janela própria (um diálogo), que não herda o modo imersivo do
 * leitor: sem isto, a barra de status (hora, bateria, sinal) aparece enquanto o painel está aberto.
 */
@Composable
private fun KeepSystemBarsHidden() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: String?,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onHighlights: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar à biblioteca")
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!chapter.isNullOrBlank()) {
                    Text(
                        chapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onHighlights) {
                Icon(Icons.Filled.FormatQuote, contentDescription = "Destaques")
            }
            IconButton(onClick = onContents) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Sumário")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.TextFields, contentDescription = "Aparência do texto")
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(progression: Double, onSeek: (Double) -> Unit) {
    // Valor local enquanto o dedo arrasta; a navegação acontece só ao soltar.
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else progression.toFloat()

    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = shown,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { dragging = false; onSeek(dragValue.toDouble()) },
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(shown * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun TableOfContents(links: List<Pair<Int, Link>>, onSelect: (Link) -> Unit) {
    if (links.isEmpty()) {
        Text("Este livro não possui sumário.", Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.padding(bottom = 24.dp)) {
        items(links) { (depth, link) ->
            Text(
                link.title ?: link.href.toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(link) }
                    .padding(start = (24 + depth * 16).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }
}

/** Achata o sumário hierárquico em pares (nível, link). */
private fun List<Link>.flatten(depth: Int = 0): List<Pair<Int, Link>> =
    flatMap { listOf(depth to it) + it.children.flatten(depth + 1) }
