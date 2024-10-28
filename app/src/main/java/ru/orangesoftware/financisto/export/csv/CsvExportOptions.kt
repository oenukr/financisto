package ru.orangesoftware.financisto.export.csv

import android.content.Intent

import ru.orangesoftware.financisto.activity.CsvExportActivity
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.CurrencyExportPreferences
import java.text.NumberFormat

data class CsvExportOptions(
    val amountFormat: NumberFormat,
    val fieldSeparator: Char,
    val includeHeader: Boolean,
    val exportSplits: Boolean,
    val uploadToDropbox: Boolean,
    val filter: WhereFilter,
    val writeUtfBom: Boolean,
) {
    companion object {
        @JvmStatic
        fun fromIntent(data: Intent): CsvExportOptions {
            val filter: WhereFilter = WhereFilter.fromIntent(data)
            val currency: Currency = CurrencyExportPreferences.fromIntent(data, "csv")
            val fieldSeparator: Char =
                data.getCharExtra(CsvExportActivity.CSV_EXPORT_FIELD_SEPARATOR, ',')
            val includeHeader: Boolean =
                data.getBooleanExtra(CsvExportActivity.CSV_EXPORT_INCLUDE_HEADER, true)
            val exportSplits: Boolean =
                data.getBooleanExtra(CsvExportActivity.CSV_EXPORT_SPLITS, false)
            val uploadToDropbox: Boolean =
                data.getBooleanExtra(CsvExportActivity.CSV_EXPORT_UPLOAD_TO_DROPBOX, false)
            return CsvExportOptions(
                CurrencyCache.createCurrencyFormat(currency),
                fieldSeparator,
                includeHeader,
                exportSplits,
                uploadToDropbox,
                filter,
                true
            )
        }
    }
}
