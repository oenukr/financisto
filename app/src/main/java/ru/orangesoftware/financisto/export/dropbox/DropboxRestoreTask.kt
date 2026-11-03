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
import java.io.ByteArrayInputStream
import java.io.InputStream

@Suppress("DEPRECATION")
open class DropboxRestoreTask @JvmOverloads constructor(
    activity: Activity,
    dialog: ProgressDialog?,
    private val backupFile: String,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(activity, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    @Volatile
    private var downloadedBackupBytes: ByteArray? = null

    override suspend fun prepareWork(vararg params: String?) {
        currentCoroutineContext().ensureActive()
        downloadedBackupBytes = downloadBackupFile(appContext, backupFile)
    }

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
        currentCoroutineContext().ensureActive()
        val bytes = try {
            downloadedBackupBytes ?: downloadBackupFile(context, backupFile)
        } finally {
            downloadedBackupBytes = null
        }
        currentCoroutineContext().ensureActive()
        val dropbox = object : Dropbox(context) {
            override fun getFileAsStream(file: String): InputStream = ByteArrayInputStream(bytes)
        }
        DatabaseImport.createFromDropboxBackup(context, db, dropbox, backupFile).importDatabase()
        return true
    }

    internal open fun downloadBackupFile(context: Context, backupFile: String): ByteArray =
        Dropbox(context).getFileAsStream(backupFile).use { it.readBytes() }

    override fun getSuccessMessage(result: Any?): String =
        appContext.getString(R.string.restore_database_success)
}
