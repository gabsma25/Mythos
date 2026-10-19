package com.mythos.reader.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.mythos.reader.reader.epub.EpubSession
import com.mythos.reader.reader.epub.toEpubPreferences
import com.mythos.reader.ui.theme.MythosTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
            configuration = EpubNavigatorFragment.Configuration(shouldApplyInsetsPadding = false),
        )
        super.onCreate(savedInstanceState)

        enterImmersiveMode()
        followFocusModePreference()

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

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && ::session.isInitialized) {
            appContainer.epubSessions.close(bookId)
        }
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

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
