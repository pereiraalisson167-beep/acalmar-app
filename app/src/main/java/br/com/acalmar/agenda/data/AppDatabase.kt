package br.com.acalmar.agenda.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Banco de dados do app.
 *
 * Versão 1: agenda, clientes, serviços, pacotes e caixa.
 * Versão 2: anamnese, termos assinados, fotos, orçamentos, evolução das sessões e tempo de retorno.
 *
 * A passagem de uma versão para a outra é gerada pelo Room a partir dos arquivos em app/schemas,
 * e mantém os dados que já estão no celular.
 */
@Database(
    entities = [
        Client::class,
        Service::class,
        PackageOffer::class,
        ClientPackage::class,
        Appointment::class,
        TimeBlock::class,
        CashEntry::class,
        Setting::class,
        Anamnesis::class,
        Consent::class,
        Photo::class,
        Quote::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "acalmar.db")
                .build()
    }
}
