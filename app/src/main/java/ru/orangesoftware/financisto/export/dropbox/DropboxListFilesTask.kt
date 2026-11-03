// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export.dropbox

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
import ru.orangesoftware.financisto.export.drive.DropboxFileList

@Suppress("DEPRECATION")
class DropboxListFilesTask @JvmOverloads constructor(
    context: Activity,
    dialog: ProgressDialog?,
    coroutineScope: CoroutineScope? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ImportExportAsyncTask(context, dialog, coroutineScope, ioDispatcher, mainDispatcher) {

    init {
        setShowResultMessage(false)
        setListener { result ->
            @Suppress("UNCHECKED_CAST")
            val files = (result as? Array<String>)
                ?: (result as? List<String>)?.toTypedArray()
                ?: emptyArray()
            DependenciesHolder().greenRobotBus.post(DropboxFileList(files))
        }
    }

    override suspend fun executeWork(vararg params: String?): Any? =
        listFiles(context)

    override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? =
        listFiles(context)

    private suspend fun listFiles(context: Context): Array<String> =
        try {
            currentCoroutineContext().ensureActive()
            val dropbox = Dropbox(context)
            val files = dropbox.listFiles()
            files.toTypedArray()
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: Exception) {
            throw ImportExportException(R.string.dropbox_error)
        }

    override fun getSuccessMessage(result: Any?): String? = null
}
