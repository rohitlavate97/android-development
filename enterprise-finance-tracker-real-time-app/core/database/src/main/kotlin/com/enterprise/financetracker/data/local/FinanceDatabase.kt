package com.enterprise.financetracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enterprise.financetracker.data.local.converter.FinanceTypeConverters
import com.enterprise.financetracker.data.local.dao.CategoryDao
import com.enterprise.financetracker.data.local.dao.TransactionDao
import com.enterprise.financetracker.data.local.entity.CategoryEntity
import com.enterprise.financetracker.data.local.entity.TransactionEntity

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(FinanceTypeConverters::class)
abstract class FinanceDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        private const val DB_NAME = "enterprise_finance.db"

        /**
         * Example Migration from Schema v1 to v2:
         * Adds an optional attachment_url column. (Phase 8 Concept 4 & ADR 023)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN attachment_url TEXT DEFAULT NULL")
            }
        }

        fun buildDatabase(context: Context): FinanceDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FinanceDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
