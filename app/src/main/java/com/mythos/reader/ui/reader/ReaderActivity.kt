package com.mythos.reader.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mythos.reader.MainActivity
import com.mythos.reader.appContainer
import com.mythos.reader.reader.DuoDisplay
import com.mythos.reader.reader.epub.EpubSession
import com.mythos.reader.reader.epub.toEpubPreferences
import com.mythos.reader.ui.highlights.copyToClipboard
import com.mythos.reader.ui.highlights.shareText
import com.mythos.reader.ui.theme.MythosTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.util.AbsoluteUrl

class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private val bookId: Long by lazy { intent.getLongExtra(EXTRA_BOOK_ID, -1) }
    private lateinit var session: EpubSession

    private val viewModel: ReaderViewModel by viewModels {
        viewModelFactory {
            initializer {
                ReaderViewModel(
                    container = appContainer,
                    session = session,
                    initialPreferences = runBlocking { appContainer.readerPreferences.current() },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val openSession = appContainer.epubSessions.get(bookId)
        if (openSession == null) {
            // O Android encerrou o processo e restaurou esta tela sem a publicação em memória.
            // Pede à biblioteca para reabrir o livro na última posição salva.
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(null)
            startActivity(MainActivity.reopenIntent(this, bookId))
            finish()
            return
        }
        session = openSession

        // A FragmentFactory precisa estar definida antes de super.onCreate, que pode
        // recriar o navegador ao dobrar/desdobrar o aparelho ou girar a tela.
        supportFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = session.lastLocator,
            initialPreferences = viewModel.preferences.value.toEpubPreferences(),
            listener = this,
            // A área da câmera é respeitada em CSS (env(safe-area-inset-top)), para que só o
            // texto desvie dela e as ilustrações possam ocupar a tela inteira.
            configuration = EpubNavigatorFragment.Configuration(
                shouldApplyInsetsPadding = false,
                selectionActionModeCallback = selectionMenu,
            ),
        )
        super.onCreate(savedInstanceState)

        enterImmersiveMode()
        followFocusModePreference()
        followDuoPreference()

        setContent {
            MythosTheme {
                ReaderScreen(viewModel = viewModel, onBack = ::finish)
            }
        }
    }

    /**
     * Liga o modo "Leitura Mythos" só enquanto o leitor está visível. Sair do app, trocar de
     * app ou abrir as configurações desliga o modo; voltar religa (e já pega uma permissão
     * recém-concedida).
     */
    private fun followFocusModePreference() {
        val focusMode = appContainer.readingFocusMode
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    viewModel.preferences
                        .map { it.doNotDisturb }
                        .distinctUntilChanged()
                        .collect { enabled -> if (enabled) focusMode.activate() else focusMode.deactivate() }
                } finally {
                    focusMode.deactivate()
                }
            }
        }
    }

    /**
     * Duo performático: com o Fold aberto, a capa do livro fica na tela externa. Como o modo
     * de leitura, só vale com o leitor visível; ao sair do livro a tela externa se apaga.
     */
    private fun followDuoPreference() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.preferences
                    .map { it.duoCover }
                    .distinctUntilChanged()
                    .collectLatest { enabled ->
                        if (enabled) {
                            DuoDisplay.present(this@ReaderActivity) {
                                val progression by viewModel.progression.collectAsStateWithLifecycle()
                                MythosTheme { DuoCover(session.book, progression) }
                            }
                        }
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && ::session.isInitialized) {
            appContainer.epubSessions.close(bookId)
        }
    }

    private val navigator: EpubNavigatorFragment?
        get() = supportFragmentManager.fragments.firstNotNullOfOrNull { it as? EpubNavigatorFragment }

    /**
     * Menu exibido ao selecionar um trecho. O Readium substitui o menu do sistema por este,
     * então Copiar e Compartilhar precisam ser oferecidos aqui também.
     */
    private val selectionMenu = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, MENU_HIGHLIGHT, 0, "Destacar")
            menu.add(Menu.NONE, MENU_COPY, 1, android.R.string.copy)
            menu.add(Menu.NONE, MENU_SHARE, 2, "Compartilhar")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val nav = navigator ?: return false
            lifecycleScope.launch {
                // A seleção é lida antes de fechar o menu, que a desfaz.
                val selection = nav.currentSelection()?.locator
                mode.finish()
                nav.clearSelection()
                val text = selection?.text?.highlight?.trim()?.takeIf { it.isNotEmpty() } ?: return@launch
                when (item.itemId) {
                    MENU_HIGHLIGHT -> viewModel.addHighlight(selection)
                    MENU_COPY -> copyToClipboard(session.book.title, text)
                    MENU_SHARE -> shareText(
                        "“$text”\n— ${session.book.title}" + session.book.author?.let { ", $it" }.orEmpty(),
                    )
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    /** Oculta barras de sistema; um gesto na borda as revela temporariamente. */
    private fun enterImmersiveMode() {
        WindowCompat.enableEdgeToEdge(window)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
        } catch (_: ActivityNotFoundException) {
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "bookId"
        private const val MENU_HIGHLIGHT = 1
        private const val MENU_COPY = 2
        private const val MENU_SHARE = 3

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
