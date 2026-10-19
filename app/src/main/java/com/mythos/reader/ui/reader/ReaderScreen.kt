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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mythos.reader.appContainer
import com.mythos.reader.reader.epub.toEpubPreferences
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression

private enum class ReaderSheet { NONE, SETTINGS, CONTENTS }

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

    val locator: Locator? by produceState(viewModel.session.lastLocator, navigator) {
        navigator?.currentLocator?.collect { value = it }
    }

    LaunchedEffect(navigator) {
        navigator?.currentLocator?.collect(viewModel::onLocatorChanged)
    }

    LaunchedEffect(navigator, preferences) {
        navigator?.submitPreferences(preferences.toEpubPreferences())
    }

    // Toques: bordas viram a página, o centro mostra ou oculta os controles.
    // Com os controles visíveis, qualquer toque no texto apenas os oculta.
    DisposableEffect(navigator, preferences.animatedPageTurns) {
        val nav = navigator ?: return@DisposableEffect onDispose { }
        val dismissControls = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                if (!controlsVisible) return false
                controlsVisible = false
                return true
            }
        }
        val pageTurns = DirectionalNavigationAdapter(nav, animatedTransition = preferences.animatedPageTurns)
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

    Box(Modifier.fillMaxSize().background(Color(preferences.backgroundColor))) {
        AndroidFragment<EpubNavigatorFragment>(Modifier.fillMaxSize()) { navigator = it }

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
            ReaderSettings(
                preferences = preferences,
                onChange = viewModel::updatePreferences,
                focusPermissionGranted = focusPermissionGranted,
                onRequestFocusPermission = { context.startActivity(focusMode.permissionIntent()) },
            )
        }
        ReaderSheet.CONTENTS -> ModalBottomSheet(onDismissRequest = { sheet = ReaderSheet.NONE }) {
            TableOfContents(
                links = remember(publication) { publication.tableOfContents.flatten() },
                onSelect = { link ->
                    navigator?.go(link)
                    sheet = ReaderSheet.NONE
                    controlsVisible = false
                },
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: String?,
    onBack: () -> Unit,
    onContents: () -> Unit,
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
