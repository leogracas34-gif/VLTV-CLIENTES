package com.vltv.clientes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ClienteEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clienteDao(): ClienteDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        // v1 -> v2: adiciona Plano, Valor do plano e Observação ao cliente.
        // Não apaga nenhum dado já cadastrado - quem já tinha clientes salvos
        // passa a ter todos marcados como plano Mensal (R$ 40) até editar.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clientes ADD COLUMN plano TEXT NOT NULL DEFAULT 'MENSAL'")
                db.execSQL("ALTER TABLE clientes ADD COLUMN valorPlano REAL NOT NULL DEFAULT 40.0")
                db.execSQL("ALTER TABLE clientes ADD COLUMN observacao TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vltv_clientes.db"
                ).addMigrations(MIGRATION_1_2).build()
                instancia = db
                db
            }
        }
    }
}
