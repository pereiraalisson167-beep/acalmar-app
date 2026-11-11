package br.com.acalmar.agenda

import android.app.Application
import br.com.acalmar.agenda.data.AppDatabase
import br.com.acalmar.agenda.data.PhotoStore
import br.com.acalmar.agenda.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AcalmarApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: Repository by lazy { Repository(AppDatabase.build(this), PhotoStore(this)) }

    override fun onCreate() {
        super.onCreate()
        // Na primeira abertura, cadastra os serviços e preços da tabela da Karina.
        appScope.launch { repository.ensureSeeded() }
    }
}
