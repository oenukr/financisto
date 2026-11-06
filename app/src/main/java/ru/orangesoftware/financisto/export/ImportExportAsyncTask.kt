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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.bus.RefreshCurrentTab
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.Export.uploadBackupFileToDropbox
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.MyPreferences
import java.io.FileNotFoundException
import java.lang.ref.WeakReference

@Suppress("TooManyFunctions", "DEPRECATION", "SpreadOperator")
abstract class ImportExportAsyncTask @JvmOverloads constructor(
    context: Activity,
    dialog: ProgressDialog?,
    coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    protected val appContext: Context = context.applicationContext ?: context
    private val activityRef: WeakReference<Activity> = WeakReference(context)
    private val dialogRef: WeakReference<ProgressDialog> = WeakReference(dialog)

    protected val context: Activity?
        get() = activityRef.get()

    protected val dialog: ProgressDialog?
        get() = dialogRef.get()

    private val coroutineScope: CoroutineScope = coroutineScope ?: DependenciesHolder().applicationScope
    private val logger: Logger = DependenciesHolder().logger

    private var showResultMessage: Boolean = true
    private var listener: ImportExportAsyncTaskListener? = null
    private var deferDropboxUpload: Boolean = false
    private val pendingDropboxUploads: MutableList<() -> Unit> = mutableListOf()

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

    @Suppress("TooGenericExceptionCaught")
    open fun execute(vararg params: String?): Job {
        job?.cancel()
        val deferred = CompletableDeferred<Any?>()
        resultDeferred = deferred

        val newJob = coroutineScope.launch(ioDispatcher) {
            val result = try {
                ensureActive()
                executeWork(*params)
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (err: Error) {
                deferred.completeExceptionally(err)
                throw err
            } catch (ex: Exception) {
                logger.e(ex, "Unable to do import/export")
                ex
            }

            ensureActive()
            lastResult = result
            deferred.complete(result)

            withContext(mainDispatcher) {
                onPostExecute(result)
            }
        }

        job = newJob
        return newJob
    }

    protected open suspend fun prepareWork(vararg params: String?) = Unit

    protected open suspend fun executeWork(vararg params: String?): Any? {
        currentCoroutineContext().ensureActive()
        prepareWork(*params)
        val uploadActions = mutableListOf<() -> Unit>()
        val result = dbMutex.withLock {
            currentCoroutineContext().ensureActive()
            val db = DatabaseAdapter(appContext)
            db.open()
            pendingDropboxUploads.clear()
            deferDropboxUpload = true
            try {
                currentCoroutineContext().ensureActive()
                work(appContext, db, *params)
            } finally {
                deferDropboxUpload = false
                uploadActions.addAll(pendingDropboxUploads)
                pendingDropboxUploads.clear()
                db.close()
            }
        }
        for (uploadAction in uploadActions) {
            currentCoroutineContext().ensureActive()
            uploadAction()
        }
        return result
    }

    fun get(): Any? = runBlocking {
        resultDeferred.await()
    }

    fun cancel(): Boolean {
        val currentJob = job
        currentJob?.cancel()
        return currentJob != null
    }

    abstract suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any?

    abstract fun getSuccessMessage(result: Any?): String?

    @Throws(FileNotFoundException::class, ImportExportException::class)
    protected open fun doUploadToDropbox(context: Context, backupFileName: String) {
        if (deferDropboxUpload) {
            pendingDropboxUploads.add { doUploadToDropbox(context, backupFileName) }
            return
        }
        if (MyPreferences.isDropboxUploadBackups(context)) {
            doForceUploadToDropbox(context, backupFileName)
        }
    }

    @Throws(FileNotFoundException::class, ImportExportException::class)
    protected open fun doForceUploadToDropbox(context: Context, backupFileName: String) {
        if (deferDropboxUpload) {
            pendingDropboxUploads.add { doForceUploadToDropbox(context, backupFileName) }
            return
        }
        publishProgress(context.getString(R.string.dropbox_uploading_file))
        uploadBackupToDropbox(context, backupFileName)
    }

    @Throws(FileNotFoundException::class, ImportExportException::class)
    internal open fun uploadBackupToDropbox(context: Context, backupFileName: String) {
        uploadBackupFileToDropbox(context, backupFileName)
    }

    protected open fun publishProgress(vararg values: String?) {
        coroutineScope.launch(mainDispatcher) {
            onProgressUpdate(*values)
        }
    }

    protected open fun onProgressUpdate(vararg values: String?) {
        val activity = context
        val currentDialog = dialog
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && currentDialog != null) {
            val message = values.firstOrNull() ?: return
            currentDialog.setMessage(message)
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
        val activity = context
        val currentDialog = dialog
        if (currentDialog != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                currentDialog.dismiss()
            } catch (e: IllegalArgumentException) {
                logger.e(e, "Error dismissing progress dialog")
            }
        }
    }

    private fun showErrorDialog(exception: ImportExportException) {
        val activity = context
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        val sb = StringBuilder()
        val formatArgs = exception.formatArgs
        if (formatArgs.isNotEmpty()) {
            sb.append(activity.getString(exception.errorResId, *formatArgs))
        } else {
            sb.append(activity.getString(exception.errorResId))
        }

        if (exception.cause != null) {
            sb.append(" : ").append(exception.cause)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.fail)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun handleSuccess(result: Any?) {
        DependenciesHolder().greenRobotBus.post(RefreshCurrentTab())
        listener?.onCompleted(result)

        val activity = context
        if (showResultMessage && activity != null && !activity.isFinishing && !activity.isDestroyed) {
            val message = getSuccessMessage(result)
            Toast.makeText(activity, activity.getString(R.string.success, message), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        internal val dbMutex = Mutex()
    }
}
