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
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@Suppress("DEPRECATION", "TooGenericExceptionCaught")
open class DropboxRestoreTask @JvmOverloads constructor(
    activity: Activity,
    dialog: ProgressDialog?,
    private val backupFile: String,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(activity, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    @Volatile
    private var downloadedBackupFile: File? = null

    override suspend fun executeWork(vararg params: String?): Any? =
        try {
            super.executeWork(*params)
        } finally {
            cleanupDownloadedBackupFile()
        }

    override suspend fun prepareWork(vararg params: String?) {
        currentCoroutineContext().ensureActive()
        cleanupDownloadedBackupFile()
        downloadedBackupFile = downloadBackupFile(appContext, backupFile)
    }

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
        currentCoroutineContext().ensureActive()
        val tempFile = downloadedBackupFile ?: downloadBackupFile(context, backupFile)
        downloadedBackupFile = null
        try {
            currentCoroutineContext().ensureActive()
            val dropbox = object : Dropbox(context) {
                override fun getFileAsStream(file: String): InputStream = FileInputStream(tempFile)
            }
            DatabaseImport.createFromDropboxBackup(context, db, dropbox, backupFile).importDatabase()
            return true
        } finally {
            tempFile.delete()
        }
    }

    internal open fun downloadBackupFile(context: Context, backupFile: String): File {
        val tempFile = File.createTempFile("dropbox_restore_", ".backup", context.cacheDir)
        try {
            Dropbox(context).getFileAsStream(backupFile).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun cleanupDownloadedBackupFile() {
        downloadedBackupFile?.delete()
        downloadedBackupFile = null
    }

    override fun getSuccessMessage(result: Any?): String =
        appContext.getString(R.string.restore_database_success)
}
