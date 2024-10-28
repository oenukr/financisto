package ru.orangesoftware.financisto.export.qif

import android.content.Intent
import ru.orangesoftware.financisto.activity.QifImportActivity
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
        fun fromIntent(data: Intent): QifImportOptions {
            val filename: String =
                data.getStringExtra(QifImportActivity.QIF_IMPORT_FILENAME).orEmpty()
            val f: Int = data.getIntExtra(QifImportActivity.QIF_IMPORT_DATE_FORMAT, 0)
            val currencyId: Long = data.getLongExtra(QifImportActivity.QIF_IMPORT_CURRENCY, 1)
            val currency: Currency = CurrencyCache.getCurrencyOrEmpty(currencyId)
            return QifImportOptions(filename, if (f == 0) EU_FORMAT else US_FORMAT, currency)
        }
    }
}
