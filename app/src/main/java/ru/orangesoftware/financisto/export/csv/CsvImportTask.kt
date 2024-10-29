package ru.orangesoftware.financisto.export.csv

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.ImportExportAsyncTask
import ru.orangesoftware.financisto.export.ImportExportException

class CsvImportTask(
    activity: Activity,
    dialog: ProgressDialog,
    private val options: CsvImportOptions,
) : ImportExportAsyncTask(activity, dialog) {

    @Throws(ImportExportException::class)
    override fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any {
        try {
            val csvimport = CsvImport(context, db, options)
            csvimport.setProgressListener { percentage ->
                publishProgress(percentage.toString())
            }
            return csvimport.doImport()
        } catch (e: Exception) {
            Log.e("Financisto", "Csv import error", e)
            if (e is ImportExportException) {
                throw e
            }
            val message: String? = e.message
            when (message) {
                "Import file not found" -> throw ImportExportException(R.string.import_file_not_found)
                "Unknown category in import line" -> throw ImportExportException(R.string.import_unknown_category)
                "Unknown project in import line" -> throw ImportExportException(R.string.import_unknown_project)
                "Wrong currency in import line" -> throw ImportExportException(R.string.import_wrong_currency)
                "IllegalArgumentException" -> throw ImportExportException(R.string.import_illegal_argument_exception)
                "ParseException" -> throw ImportExportException(R.string.import_parse_error)
                else -> throw ImportExportException(R.string.csv_import_error)
            }
        }
    }

    override fun onProgressUpdate(vararg values: String?) {
        super.onProgressUpdate(*values)
        dialog.setMessage(context.getString(R.string.csv_import_inprogress_update, values[0]))
    }

    override fun getSuccessMessage(result: Any?): String = result.toString()
}
