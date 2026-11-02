// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export.qif

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.ImportExportAsyncTask
import ru.orangesoftware.financisto.export.ImportExportException
import ru.orangesoftware.financisto.utils.Logger
import kotlin.coroutines.coroutineContext

class QifImportTask @JvmOverloads constructor(
    activity: Activity,
    dialog: ProgressDialog?,
    private val options: QifImportOptions,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(activity, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    private val logger: Logger = DependenciesHolder().logger

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
        return try {
            coroutineContext.ensureActive()
            val qifImport = QifImport(context, db, options)
            qifImport.importDatabase()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e, "Qif import error")
            throw ImportExportException(R.string.qif_import_error)
        }
    }

    override fun getSuccessMessage(result: Any?): String =
        context.getString(R.string.qif_import_success)
}
