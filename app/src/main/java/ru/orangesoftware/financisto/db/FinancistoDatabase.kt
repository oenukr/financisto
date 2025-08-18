package ru.orangesoftware.financisto.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Budget
//import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
//import ru.orangesoftware.financisto.model.MyLocation
//import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.SymbolFormatConverter

//import ru.orangesoftware.financisto.model.Transaction
//import ru.orangesoftware.financisto.model.TransactionAttributeInfo

@Database(entities = [
    Account::class,
    Budget::class,
//    Category::class,
    Currency::class,
//    MyLocation::class,
//    Payee::class,
    Project::class,
//    Transaction::class,
//    TransactionAttributeInfo::class
], version = 1)
@TypeConverters(SymbolFormatConverter::class)
abstract class FinancistoDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
//    abstract fun categoryDao(): CategoryDao
    abstract fun currencyDao(): CurrencyDao
//    abstract fun myLocationDao(): MyLocationDao
//    abstract fun payeeDao(): PayeeDao
    abstract fun projectDao(): ProjectDao
//    abstract fun transactionDao(): TransactionDao
//    abstract fun transactionAttributeInfoDao(): TransactionAttributeInfoDao
}
