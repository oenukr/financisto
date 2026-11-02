// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export.dropbox

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.backup.DatabaseImport
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.ImportExportAsyncTask

@Suppress("DEPRECATION")
class DropboxRestoreTask @JvmOverloads constructor(
    activity: Activity,
    dialog: ProgressDialog?,
    private val backupFile: String,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(activity, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
        currentCoroutineContext().ensureActive()
        val dropbox = Dropbox(context)
        DatabaseImport.createFromDropboxBackup(context, db, dropbox, backupFile).importDatabase()
        return true
    }

    override fun getSuccessMessage(result: Any?): String =
        context.getString(R.string.restore_database_success)
}
