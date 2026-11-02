// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export.csv

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.ImportExportAsyncTask
import ru.orangesoftware.financisto.export.ImportExportException

@Suppress("DEPRECATION", "SpreadOperator")
class CsvImportTask @JvmOverloads constructor(
    activity: Activity,
    dialog: ProgressDialog?,
    private val options: CsvImportOptions,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(activity, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    private val logger = DependenciesHolder().logger

    @Suppress("TooGenericExceptionCaught")
    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any {
        val exToThrow = try {
            currentCoroutineContext().ensureActive()
            val csvimport = CsvImport(context, db, options)
            csvimport.setProgressListener { percentage ->
                publishProgress(percentage.toString())
            }
            val result = csvimport.doImport()
            currentCoroutineContext().ensureActive()
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImportExportException) {
            logger.e(e, "Csv import error")
            e
        } catch (e: Exception) {
            logger.e(e, "Csv import error")
            val resId = when (e.message) {
                "Import file not found" -> R.string.import_file_not_found
                "Unknown category in import line" -> R.string.import_unknown_category
                "Unknown project in import line" -> R.string.import_unknown_project
                "Wrong currency in import line" -> R.string.import_wrong_currency
                "IllegalArgumentException" -> R.string.import_illegal_argument_exception
                "ParseException" -> R.string.import_parse_error
                else -> R.string.csv_import_error
            }
            ImportExportException(resId)
        }
        throw exToThrow
    }

    override fun onProgressUpdate(vararg values: String?) {
        super.onProgressUpdate(*values)
        if (context.isFinishing || context.isDestroyed) return
        dialog?.setMessage(context.getString(R.string.csv_import_inprogress_update, values.firstOrNull()))
    }

    override fun getSuccessMessage(result: Any?): String = result.toString()
}
