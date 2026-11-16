package br.com.acalmar.agenda.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import br.com.acalmar.agenda.data.AppJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

// O que a pessoa digitou em um formulário precisa sobreviver quando o Android fecha o app em
// segundo plano (por exemplo, ao ir para o WhatsApp no meio de uma ficha) ou recria a tela.
// Valores simples usam `rememberSaveable` direto; listas e objetos são guardados como texto JSON.

/** Lista de formulário que não se perde quando a tela é recriada. */
@Composable
fun <T> rememberSavedList(serializer: KSerializer<T>): SnapshotStateList<T> {
    val list = ListSerializer(serializer)
    return rememberSaveable(
        saver = Saver<SnapshotStateList<T>, String>(
            save = { AppJson.encodeToString(list, it.toList()) },
            restore = { text -> AppJson.decodeFromString(list, text).toMutableStateList() }
        )
    ) { mutableStateListOf() }
}

/** Objeto de formulário (pode ser nulo) que não se perde quando a tela é recriada. */
@Composable
fun <T : Any> rememberSavedObject(serializer: KSerializer<T>, initial: T? = null): MutableState<T?> =
    rememberSaveable(
        saver = Saver<MutableState<T?>, String>(
            save = { state -> state.value?.let { AppJson.encodeToString(serializer, it) } ?: "" },
            restore = { text ->
                mutableStateOf(if (text.isEmpty()) null else AppJson.decodeFromString(serializer, text))
            }
        )
    ) { mutableStateOf(initial) }
