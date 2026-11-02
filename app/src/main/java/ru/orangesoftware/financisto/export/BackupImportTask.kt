// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.backup.DatabaseImport
import ru.orangesoftware.financisto.db.DatabaseAdapter
import kotlin.coroutines.coroutineContext

@Suppress("DEPRECATION")
class BackupImportTask @JvmOverloads constructor(
    activity: Activity,
    dialog: ProgressDialog?,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(activity, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any {
        coroutineContext.ensureActive()
        val filename = params.getOrNull(0) ?: throw IllegalArgumentException("Backup file path required")
        DatabaseImport.createFromFileBackup(context, db, filename).importDatabase()
        return true
    }

    override fun getSuccessMessage(result: Any?): String =
        context.getString(R.string.restore_database_success)
}
