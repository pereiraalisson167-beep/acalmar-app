package br.com.acalmar.agenda

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import br.com.acalmar.agenda.ui.LocalRepo
import br.com.acalmar.agenda.ui.nav.AcalmarRoot
import br.com.acalmar.agenda.ui.theme.AcalmarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // O app é sempre claro: ícones escuros nas barras do sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        val repository = (application as AcalmarApp).repository
        setContent {
            AcalmarTheme {
                CompositionLocalProvider(LocalRepo provides repository) {
                    AcalmarRoot()
                }
            }
        }
    }
}
