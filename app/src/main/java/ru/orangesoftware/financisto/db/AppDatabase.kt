package ru.orangesoftware.financisto.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.orangesoftware.financisto.db.dao.*
import ru.orangesoftware.financisto.db.entity.*

@Database(
    entities = [
        AccountEntity::class,
        BudgetEntity::class,
        CategoryEntity::class,
        CurrencyEntity::class,
        ExchangeRateEntity::class,
        LocationEntity::class,
        PayeeEntity::class,
        ProjectEntity::class,
        TransactionEntity::class,
        AttributeEntity::class,
        SmsTemplateEntity::class,
        CategoryAttributeCrossRef::class,
        TransactionAttributeValueEntity::class,
        CreditCardClosingDateEntity::class
    ],
    version = 218, // From Database.kt DB_VERSION
    exportSchema = true // Recommended to keep schema history
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun locationDao(): LocationDao
    abstract fun payeeDao(): PayeeDao
    abstract fun projectDao(): ProjectDao
    abstract fun transactionDao(): TransactionDao
    abstract fun attributeDao(): AttributeDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun categoryAttributeCrossRefDao(): CategoryAttributeCrossRefDao
    abstract fun transactionAttributeValueDao(): TransactionAttributeValueDao
    abstract fun creditCardClosingDateDao(): CreditCardClosingDateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Define the empty migration
        val MIGRATION_217_218 = object : Migration(217, 218) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Since we are trying to match the existing schema at version 218,
                // this migration can be kept empty initially.
                // If Room finds discrepancies, this is where SQL commands to adjust
                // the schema from a hypothetical 217 to the Room-defined 218 would go.
                // For example, if a new column was added in version 218 by the old system,
                // and our entity reflects that, but we are migrating *from* 217, that ALTER TABLE would go here.
                // For now, we assume entities match the final state of version 218.
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DatabaseHelper.DATABASE_NAME // Ensure this matches your existing DB name
                )
                .addMigrations(MIGRATION_217_218) // Add this line
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
