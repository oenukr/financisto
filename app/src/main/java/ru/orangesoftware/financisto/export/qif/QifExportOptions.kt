package ru.orangesoftware.financisto.export.qif

import android.content.Intent
import ru.orangesoftware.financisto.activity.QifExportActivity
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.CurrencyExportPreferences
import java.text.DateFormat
import java.text.SimpleDateFormat

data class QifExportOptions(
    val currency: Currency,
    val dateFormat: DateFormat,
    val selectedAccounts: LongArray?,
    val filter: WhereFilter,
    val uploadToDropbox: Boolean,
) {
    companion object {
        const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"

        @JvmStatic
        fun fromIntent(data: Intent): QifExportOptions {
            val filter: WhereFilter = WhereFilter.fromIntent(data)
            val currency: Currency = CurrencyExportPreferences.fromIntent(data, "qif")
            val dateFormat: DateFormat = SimpleDateFormat(
                data.getStringExtra(QifExportActivity.QIF_EXPORT_DATE_FORMAT).orEmpty()
            )
            val selectedAccounts: LongArray? =
                data.getLongArrayExtra(QifExportActivity.QIF_EXPORT_SELECTED_ACCOUNTS)
            val uploadToDropbox: Boolean =
                data.getBooleanExtra(QifExportActivity.QIF_EXPORT_UPLOAD_TO_DROPBOX, false)
            return QifExportOptions(currency, dateFormat, selectedAccounts, filter, uploadToDropbox)
        }
    }
}
