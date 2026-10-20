package com.mythos.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mythos.reader.ui.highlights.HighlightsScreen
import com.mythos.reader.ui.highlights.asShareText
import com.mythos.reader.ui.highlights.copyToClipboard
import com.mythos.reader.ui.highlights.shareText
import com.mythos.reader.ui.library.LibraryEvent
import com.mythos.reader.ui.library.LibraryScreen
import com.mythos.reader.ui.library.LibraryViewModel
import com.mythos.reader.ui.reader.ReaderActivity
import com.mythos.reader.ui.theme.MythosTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: LibraryViewModel by viewModels {
        viewModelFactory { initializer { LibraryViewModel(appContainer) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncomingIntent(intent)

        setContent {
            MythosTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var showHighlights by rememberSaveable { mutableStateOf(false) }

                // Alguns gerenciadores de arquivos informam EPUB como octet-stream;
                // o importador valida o conteúdo de qualquer forma.
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris -> viewModel.import(uris) }
                val folderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri -> viewModel.addFolder(uri) }

                // Ao voltar para a biblioteca (de outro app ou do leitor), procura EPUBs novos nas pastas.
                LifecycleStartEffect(Unit) {
                    viewModel.syncFolders()
                    onStopOrDispose { }
                }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LibraryEvent.OpenReader ->
                                startActivity(ReaderActivity.intent(this@MainActivity, event.bookId))
                            is LibraryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                        }
                    }
                }

                if (showHighlights) {
                    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
                    HighlightsScreen(
                        books = highlights,
                        onBack = { showHighlights = false },
                        onOpen = viewModel::open,
                        onCopy = { copyToClipboard("Destaque", it.text) },
                        onShareBook = { shareText(it.asShareText()) },
                        onDelete = viewModel::deleteHighlight,
                    )
                    return@MythosTheme
                }

                LibraryScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onFilterChange = viewModel::setFilter,
                    onImport = {
                        picker.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/octet-stream",
                                "text/plain",
                                "text/html",
                                "application/xhtml+xml",
                            ),
                        )
                    },
                    onOpen = { viewModel.open(it.id) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = viewModel::delete,
                    onAddFolder = { folderPicker.launch(null) },
                    onRemoveFolder = viewModel::removeFolder,
                    onOpenHighlights = { showHighlights = true },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Arquivos abertos pelo gerenciador de arquivos ou compartilhados com o app. */
    private fun handleIncomingIntent(intent: Intent) {
        val reopenId = intent.getLongExtra(EXTRA_REOPEN_BOOK_ID, -1)
        if (reopenId >= 0) {
            viewModel.open(reopenId)
            return
        }

        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
            else -> emptyList()
        }
        viewModel.import(uris)
    }

    companion object {
        private const val EXTRA_REOPEN_BOOK_ID = "reopenBookId"

        fun reopenIntent(context: Context, bookId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_REOPEN_BOOK_ID, bookId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
