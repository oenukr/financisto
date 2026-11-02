// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export.qif

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.ImportExportAsyncTask
import kotlin.coroutines.coroutineContext

class QifExportTask @JvmOverloads constructor(
    context: Activity,
    dialog: ProgressDialog?,
    private val options: QifExportOptions,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(context, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
        coroutineContext.ensureActive()
        val qifExport = QifExport(context, db, options)
        val backupFileName = qifExport.export()
        coroutineContext.ensureActive()
        if (options.uploadToDropbox) {
            doUploadToDropbox(context, backupFileName)
        }
        return backupFileName
    }

    override fun getSuccessMessage(result: Any?): String? = result?.toString()
}
