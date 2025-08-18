package ru.orangesoftware.financisto.export.qif

import android.content.Context
import android.content.Intent
import androidx.room.Room
import ru.orangesoftware.financisto.activity.QifImportActivity
import ru.orangesoftware.financisto.db.FinancistoDatabase
import ru.orangesoftware.financisto.export.qif.QifDateFormat.EU_FORMAT
import ru.orangesoftware.financisto.export.qif.QifDateFormat.US_FORMAT
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.CurrencyCache

data class QifImportOptions(
    val filename: String,
    val dateFormat: QifDateFormat,
    val currency: Currency,
) {
    companion object {
        @JvmStatic
        fun fromIntent(context: Context, data: Intent): QifImportOptions {
            val filename: String =
                data.getStringExtra(QifImportActivity.QIF_IMPORT_FILENAME).orEmpty()
            val f: Int = data.getIntExtra(QifImportActivity.QIF_IMPORT_DATE_FORMAT, 0)
            val currencyId: Long = data.getLongExtra(QifImportActivity.QIF_IMPORT_CURRENCY, 1)
            val roomDb = Room.databaseBuilder(context.applicationContext,
                FinancistoDatabase::class.java, "financisto.db").build()
            val currencyCache = CurrencyCache(roomDb.currencyDao())
            val currency: Currency = currencyCache.getCurrencyOrEmpty(currencyId)
            return QifImportOptions(filename, if (f == 0) EU_FORMAT else US_FORMAT, currency)
        }
    }
}
