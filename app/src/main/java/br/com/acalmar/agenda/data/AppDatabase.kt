package br.com.acalmar.agenda.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Client::class,
        Service::class,
        PackageOffer::class,
        ClientPackage::class,
        Appointment::class,
        TimeBlock::class,
        CashEntry::class,
        Setting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "acalmar.db")
                .build()
    }
}
