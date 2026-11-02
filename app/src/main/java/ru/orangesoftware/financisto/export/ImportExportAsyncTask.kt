// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.bus.RefreshCurrentTab
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.Export.uploadBackupFileToDropbox
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.MyPreferences
import java.io.FileNotFoundException

@Suppress("TooManyFunctions")
abstract class ImportExportAsyncTask @JvmOverloads constructor(
    protected val context: Activity,
    protected val dialog: ProgressDialog?,
    coroutineScope: CoroutineScope? = null,
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    protected val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    protected val coroutineScope: CoroutineScope = coroutineScope ?: DependenciesHolder().applicationScope
    private val logger: Logger = DependenciesHolder().logger

    private var showResultMessage: Boolean = true
    private var listener: ImportExportAsyncTaskListener? = null

    var job: Job? = null
        private set

    @Volatile
    var lastResult: Any? = null
        private set

    val isCancelled: Boolean
        get() = job?.isCancelled == true

    private var resultDeferred: CompletableDeferred<Any?> = CompletableDeferred()

    fun setListener(listener: ImportExportAsyncTaskListener?) {
        this.listener = listener
    }

    fun setShowResultMessage(showResultMessage: Boolean) {
        this.showResultMessage = showResultMessage
    }

    open fun execute(vararg params: String?): Job {
        job?.cancel()
        val deferred = CompletableDeferred<Any?>()
        resultDeferred = deferred

        val newJob = coroutineScope.launch(ioDispatcher) {
            val result = try {
                coroutineContext.ensureActive()
                val db = DatabaseAdapter(context)
                db.open()
                try {
                    coroutineContext.ensureActive()
                    work(context, db, *params)
                } finally {
                    db.close()
                }
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (ex: Exception) {
                logger.e(ex, "Unable to do import/export")
                ex
            }

            coroutineContext.ensureActive()
            lastResult = result
            deferred.complete(result)

            withContext(mainDispatcher) {
                onPostExecute(result)
            }
        }

        job = newJob
        return newJob
    }

    fun get(): Any? = runBlocking {
        resultDeferred.await()
    }

    fun cancel(mayInterruptIfRunning: Boolean = true): Boolean {
        val currentJob = job
        currentJob?.cancel()
        return currentJob != null
    }

    abstract suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any?

    abstract fun getSuccessMessage(result: Any?): String?

    @Throws(FileNotFoundException::class, ImportExportException::class)
    protected open fun doUploadToDropbox(context: Context, backupFileName: String) {
        if (MyPreferences.isDropboxUploadBackups(context)) {
            doForceUploadToDropbox(context, backupFileName)
        }
    }

    @Throws(FileNotFoundException::class, ImportExportException::class)
    protected open fun doForceUploadToDropbox(context: Context, backupFileName: String) {
        publishProgress(context.getString(R.string.dropbox_uploading_file))
        uploadBackupFileToDropbox(context, backupFileName)
    }

    protected open fun publishProgress(vararg values: String?) {
        coroutineScope.launch(mainDispatcher) {
            onProgressUpdate(*values)
        }
    }

    protected open fun onProgressUpdate(vararg values: String?) {
        if (!context.isFinishing && !context.isDestroyed && dialog != null) {
            val message = values.firstOrNull() ?: return
            dialog.setMessage(message)
        }
    }

    protected open fun onPostExecute(result: Any?) {
        dismissDialogSafely()

        when (result) {
            is ImportExportException -> showErrorDialog(result)
            is Exception -> Unit
            else -> handleSuccess(result)
        }
    }

    private fun dismissDialogSafely() {
        if (dialog != null && !context.isFinishing && !context.isDestroyed) {
            try {
                dialog.dismiss()
            } catch (e: IllegalArgumentException) {
                logger.e(e, "Error dismissing progress dialog")
            }
        }
    }

    private fun showErrorDialog(exception: ImportExportException) {
        if (context.isFinishing || context.isDestroyed) return

        val sb = StringBuilder()
        val formatArgs = exception.formatArgs
        if (formatArgs.isNotEmpty()) {
            sb.append(context.getString(exception.errorResId, *formatArgs))
        } else {
            sb.append(context.getString(exception.errorResId))
        }

        if (exception.cause != null) {
            sb.append(" : ").append(exception.cause)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.fail)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun handleSuccess(result: Any?) {
        val message = getSuccessMessage(result)

        DependenciesHolder().greenRobotBus.post(RefreshCurrentTab())
        listener?.onCompleted(result)

        if (showResultMessage && !context.isFinishing && !context.isDestroyed) {
            Toast.makeText(context, context.getString(R.string.success, message), Toast.LENGTH_LONG).show()
        }
    }
}
