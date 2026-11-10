package br.com.acalmar.agenda.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import br.com.acalmar.agenda.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Acesso aos dados a partir de qualquer tela. */
val LocalRepo = staticCompositionLocalOf<Repository> { error("Repository não fornecido") }

/** Mostra avisos curtos no rodapé ("Agendamento salvo", etc.). */
class Notifier(private val scope: CoroutineScope, private val host: SnackbarHostState) {
    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message)
        }
    }
}

val LocalNotifier = staticCompositionLocalOf<Notifier> { error("Notifier não fornecido") }
