package ru.orangesoftware.financisto.export.csv

import android.content.Intent
import ru.orangesoftware.financisto.activity.CsvImportActivity
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.CurrencyExportPreferences
import java.text.DateFormat
import java.text.SimpleDateFormat

data class CsvImportOptions(
    val currency: Currency,
    val dateFormat: DateFormat,
    val selectedAccountId: Long,
    val filter: WhereFilter,
    val filename: String?,
    val fieldSeparator: Char,
    var useHeaderFromFile: Boolean,
) {
    companion object {
        const val DEFAULT_DATE_FORMAT = "dd.MM.yyyy"

        @JvmStatic
        fun fromIntent(data: Intent): CsvImportOptions {
            val filter: WhereFilter = WhereFilter.fromIntent(data)
            val currency: Currency = CurrencyExportPreferences.fromIntent(data, "csv")
            val fieldSeparator: Char =
                data.getCharExtra(CsvImportActivity.CSV_IMPORT_FIELD_SEPARATOR, ',')
            val dateFormat: String =
                data.getStringExtra(CsvImportActivity.CSV_IMPORT_DATE_FORMAT) ?: DEFAULT_DATE_FORMAT
            val selectedAccountId: Long =
                data.getLongExtra(CsvImportActivity.CSV_IMPORT_SELECTED_ACCOUNT_2, -1)
            val filename: String =
                data.getStringExtra(CsvImportActivity.CSV_IMPORT_FILENAME).orEmpty()
            val useHeaderFromFile: Boolean =
                data.getBooleanExtra(CsvImportActivity.CSV_IMPORT_USE_HEADER_FROM_FILE, true)
            return CsvImportOptions(
                currency,
                SimpleDateFormat(dateFormat),
                selectedAccountId,
                filter,
                filename,
                fieldSeparator,
                useHeaderFromFile
            )
        }
    }
}
