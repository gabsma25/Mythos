package com.mythos.reader.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import kotlin.coroutines.resume
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * "Duo performático": com o Fold aberto, usa a tela externa (traseira, nesse momento) como
 * um segundo mostrador, no estilo do Duo do iPhone. É o modo Dual Screen do Android: o leitor
 * continua na tela interna e a externa recebe uma apresentação própria.
 *
 * Não exige permissão; a One UI apenas mostra o aviso "A tela dupla está ativada", com a
 * opção Desligar. Ao fechar o aparelho, o sistema encerra a apresentação; ao abrir de novo,
 * ela volta sozinha.
 */
@OptIn(ExperimentalWindowApi::class, FlowPreview::class)
object DuoDisplay {

    /**
     * Mantém [content] na tela externa enquanto a coroutine estiver ativa. Retorna só se o
     * aparelho não tiver tela externa; cancelar a coroutine encerra a apresentação.
     */
    suspend fun present(activity: ComponentActivity, content: @Composable () -> Unit) {
        val controller = runCatching { WindowAreaController.getOrCreate() }.getOrNull() ?: return
        controller.windowAreaInfos
            .map { infos -> infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING } }
            // ACTIVE é ignorado: durante a própria apresentação, tratá-lo como indisponível a
            // encerraria; ao desdobrar, tratá-lo como disponível faria a Activity recriada
            // tentar apresentar por cima da sessão que a Activity anterior ainda está fechando.
            .filter { it?.presentStatus != WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE }
            .map { info -> info?.token?.takeIf { info.presentStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE } }
            // Mesmo token de antes: a sessão foi encerrada pelo usuário ("Desligar" no aviso
            // do sistema) e não é reaberta até o aparelho ser fechado e aberto de novo.
            .distinctUntilChanged()
            // Ao dobrar/desdobrar, a Activity é recriada em seguida; esperar um pouco evita
            // que a instância de saída abra uma apresentação que duraria uma fração de segundo.
            .debounce(SETTLE_MS)
            .collectLatest { token -> if (token != null) presentUntilEnded(controller, token, activity, content) }
    }

    private val WindowAreaInfo.presentStatus: WindowAreaCapability.Status?
        get() = getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status

    private const val SETTLE_MS = 300L

    /**
     * Suspende enquanto a apresentação durar. Se o sistema a encerrar (aparelho fechado),
     * retorna e aguarda a tela externa ficar disponível de novo, sem insistir em loop.
     */
    private suspend fun presentUntilEnded(
        controller: WindowAreaController,
        token: android.os.Binder,
        activity: ComponentActivity,
        content: @Composable () -> Unit,
    ) {
        var session: WindowAreaSessionPresenter? = null
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val callback = object : WindowAreaPresentationSessionCallback {
                    override fun onSessionStarted(presenter: WindowAreaSessionPresenter) {
                        if (!continuation.isActive) return presenter.close()
                        // A janela da apresentação não pertence à Activity: os "donos" de ciclo
                        // de vida que o Compose procura precisam ser repassados à mão.
                        val view = ComposeView(presenter.context).apply {
                            setViewTreeLifecycleOwner(activity)
                            setViewTreeViewModelStoreOwner(activity)
                            setViewTreeSavedStateRegistryOwner(activity)
                            setContent(content)
                        }
                        presenter.setContentView(view)
                        session = presenter
                    }

                    override fun onSessionEnded(t: Throwable?) {
                        session = null
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onContainerVisibilityChanged(isVisible: Boolean) {}
                }
                // Nunca deixa uma falha do sistema derrubar o leitor.
                runCatching {
                    controller.presentContentOnWindowArea(token, activity, activity.mainExecutor, callback)
                }.onFailure { if (continuation.isActive) continuation.resume(Unit) }
            }
        } finally {
            runCatching { session?.close() }
        }
    }
}
