package ru.orangesoftware.financisto.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.orangesoftware.financisto.db.CurrencyDao
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.service.RecurrenceScheduler
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.IntegrityFix
import java.io.IOException

abstract class FullDatabaseImport(
    protected val context: Context,
    protected val dbAdapter: DatabaseAdapter,
    protected val currencyDao: CurrencyDao
) {
    protected val db: SupportSQLiteDatabase = dbAdapter.db()
    protected val currencyCache: CurrencyCache = CurrencyCache(currencyDao)

    @Throws(IOException::class)
    fun importDatabase() {
        db.beginTransaction()
        try {
            cleanDatabase()
            restoreDatabase()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        IntegrityFix(dbAdapter).fix()
        currencyCache.initialize()
        scheduleAll()
    }

    @Throws(IOException::class)
    protected abstract fun restoreDatabase()

    private fun cleanDatabase() {
        tablesToClean().forEach {
            db.execSQL("delete from $it")
        }
    }

    protected open fun tablesToClean(): List<String> =
        Backup.BACKUP_TABLES.asList().plus("running_balance")

    private fun scheduleAll() = RecurrenceScheduler(dbAdapter).scheduleAll(context)
}
