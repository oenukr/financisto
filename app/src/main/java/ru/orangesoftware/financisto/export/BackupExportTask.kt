// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import ru.orangesoftware.financisto.backup.DatabaseExport
import ru.orangesoftware.financisto.db.DatabaseAdapter
import kotlin.coroutines.coroutineContext

class BackupExportTask @JvmOverloads constructor(
    context: Activity,
    dialog: ProgressDialog?,
    private val uploadOnline: Boolean,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(context, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    @Volatile
    var backupFileName: String? = null

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
        coroutineContext.ensureActive()
        val export = DatabaseExport(context, db.db(), true)
        val name = export.export()
        backupFileName = name
        coroutineContext.ensureActive()
        if (uploadOnline && name != null) {
            doUploadToDropbox(context, name)
        }
        return name
    }

    override fun getSuccessMessage(result: Any?): String = result?.toString() ?: ""
}
