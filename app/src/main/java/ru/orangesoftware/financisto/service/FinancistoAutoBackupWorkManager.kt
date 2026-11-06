// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.backup.DatabaseExport
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.export.ImportExportAsyncTask
import ru.orangesoftware.financisto.export.dropbox.Dropbox
import ru.orangesoftware.financisto.service.DailyAutoBackupScheduler.Companion.scheduleNextAutoBackup
import ru.orangesoftware.financisto.utils.MyPreferences
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class FinancistoAutoBackupWorkManager(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams), NotificationPresentation {
    private val logger = DependenciesHolder().logger
    private val stopSignal = CompletableDeferred<Unit>()

    @Volatile
    private var activeDropbox: Dropbox? = null

    private val db = DatabaseAdapter(context).also { it.open() }

    override fun onStopped() {
        stopSignal.complete(Unit)
        activeDropbox?.cancelUpload()
        db.close()
        super.onStopped()
    }

    override fun doWork(): Result {
        var successful = true
        try {
            try {
                logger.e("Auto-backup started at ${System.currentTimeMillis()}")
                val duration = measureTime {
                    val fileName = runBlocking {
                        exportDatabaseWhenLocked()
                    } ?: return Result.failure()
                    if (isStopped || stopSignal.isCompleted) return Result.failure()
                    if (MyPreferences.isDropboxUploadAutoBackups(applicationContext)) {
                        successful = uploadToDropbox(fileName)
                        if (isStopped || stopSignal.isCompleted) return Result.failure()
                    }
                    if (MyPreferences.isGoogleDriveUploadAutoBackups(applicationContext)) {
                        successful = uploadToGoogleDrive(fileName)
                    }
                }
                logger.e("Auto-backup completed in ${duration.inWholeMilliseconds}ms")
                if (successful) {
                    MyPreferences.notifyAutobackupSucceeded(applicationContext)
                }
            } catch (e: Exception) {
                logger.e(e, "Auto-backup unsuccessful")
                MyPreferences.notifyAutobackupFailed(applicationContext, e)
                successful = false
            }
        } finally {
            scheduleNextAutoBackup(applicationContext)
        }

        return if (successful) Result.success() else Result.failure()
    }

    private suspend fun exportDatabaseWhenLocked(): String? {
        val mutex = ImportExportAsyncTask.dbMutex
        while (!isStopped && !stopSignal.isCompleted) {
            if (mutex.tryLock()) {
                return try {
                    if (isStopped || stopSignal.isCompleted) {
                        null
                    } else {
                        DatabaseExport(applicationContext, db.db(), true).export()
                    }
                } finally {
                    mutex.unlock()
                }
            }
            withTimeoutOrNull(LOCK_POLL_INTERVAL_MS.milliseconds) {
                stopSignal.await()
            }
        }
        return null
    }

    private fun uploadToGoogleDrive(fileName: String): Boolean {
        var successful: Boolean
        try {
            successful = Export.uploadBackupFileToGoogleDrive(applicationContext, fileName)
        } catch (e: Exception) {
            logger.e(e, "Unable to upload auto-backup to Google Drive")
            MyPreferences.notifyAutobackupFailed(applicationContext, e)
            successful = false
        }
        return successful
    }

    private fun uploadToDropbox(fileName: String): Boolean {
        var successful: Boolean
        val dropbox = Dropbox(applicationContext)
        activeDropbox = dropbox
        try {
            successful = Export.uploadBackupFileToDropbox(
                applicationContext,
                dropbox,
                fileName,
            ) { isStopped || stopSignal.isCompleted }
        } catch (e: Exception) {
            logger.e(e, "Unable to upload auto-backup to Dropbox")
            if (!isStopped && !stopSignal.isCompleted) {
                MyPreferences.notifyAutobackupFailed(applicationContext, e)
            }
            successful = false
        } finally {
            activeDropbox = null
        }
        return successful
    }

    companion object {
        private const val LOCK_POLL_INTERVAL_MS = 50L
    }
}
