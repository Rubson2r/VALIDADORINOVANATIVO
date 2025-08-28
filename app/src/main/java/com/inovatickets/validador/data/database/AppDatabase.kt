package com.inovatickets.validador.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.inovatickets.validador.data.database.entities.*
import com.inovatickets.validador.data.database.dao.*
import com.inovatickets.validador.utils.Converters

@Database(
    entities = [
        Evento::class,
        Setor::class, // Adicionada a entidade Setor
        Codigo::class,
        LogEntry::class,
        Configuracao::class
    ],
    version = 3, // Versão incrementada para 3
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun eventoDao(): EventoDao
    abstract fun setorDao(): SetorDao // Adicionado o SetorDao
    abstract fun codigoDao(): CodigoDao
    abstract fun logDao(): LogDao
    abstract fun configuracaoDao(): ConfiguracaoDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "validador_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}