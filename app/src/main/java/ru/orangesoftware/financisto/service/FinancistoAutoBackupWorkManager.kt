package ru.orangesoftware.financisto.service

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.orangesoftware.financisto.backup.DatabaseExport
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.service.DailyAutoBackupScheduler.Companion.scheduleNextAutoBackup
import ru.orangesoftware.financisto.utils.MyPreferences
import kotlin.time.measureTime

private const val TAG: String = "FinancistoAutoBackupWorkManager"

class FinancistoAutoBackupWorkManager(
    context: Context,
    workerParams: WorkerParameters,
): Worker(context, workerParams), NotificationPresentation {
    private val db = DatabaseAdapter(context).also { it.open() }

    override fun onStopped() {
        db.close()
        super.onStopped()
    }

    override fun doWork(): Result {
        var successful = true
        try {
            try {
                Log.e(TAG, "Auto-backup started at ${System.currentTimeMillis()}")
                val duration = measureTime {
                    val fileName = DatabaseExport(applicationContext, db.db(), true).export()
                    if (MyPreferences.isDropboxUploadAutoBackups(applicationContext)) {
                        successful = uploadToDropbox(fileName)
                    }
                    if (MyPreferences.isGoogleDriveUploadAutoBackups(applicationContext)) {
                        successful = uploadToGoogleDrive(fileName)
                    }
                }
                Log.e(
                    TAG,
                    "Auto-backup completed in ${duration.inWholeMilliseconds}ms"
                )
                if (successful) {
                    MyPreferences.notifyAutobackupSucceeded(applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-backup unsuccessful", e)
                MyPreferences.notifyAutobackupFailed(applicationContext, e)
                successful = false
            }
        } finally {
            scheduleNextAutoBackup(applicationContext)
        }

        return if (successful) Result.success() else Result.failure()
    }

    private fun uploadToGoogleDrive(fileName: String): Boolean {
        var successful: Boolean
        try {
            successful = Export.uploadBackupFileToGoogleDrive(applicationContext, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to upload auto-backup to Google Drive", e)
            MyPreferences.notifyAutobackupFailed(applicationContext, e)
            successful = false
        }
        return successful
    }

    private fun uploadToDropbox(fileName: String): Boolean {
        var successful: Boolean
        try {
            successful = Export.uploadBackupFileToDropbox(applicationContext, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to upload auto-backup to Dropbox", e)
            MyPreferences.notifyAutobackupFailed(applicationContext, e)
            successful = false
        }
        return successful
    }
}
