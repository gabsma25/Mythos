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
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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

                // Alguns gerenciadores de arquivos informam EPUB como octet-stream;
                // o importador valida o conteúdo de qualquer forma.
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris -> viewModel.import(uris) }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LibraryEvent.OpenReader ->
                                startActivity(ReaderActivity.intent(this@MainActivity, event.bookId))
                            is LibraryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                        }
                    }
                }

                LibraryScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onFilterChange = viewModel::setFilter,
                    onImport = { picker.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                    onOpen = { viewModel.open(it) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = viewModel::delete,
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
