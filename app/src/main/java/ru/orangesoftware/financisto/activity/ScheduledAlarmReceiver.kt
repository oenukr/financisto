package ru.orangesoftware.financisto.activity;

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ru.orangesoftware.financisto.service.FinancistoAutoBackupWorkManager
import ru.orangesoftware.financisto.service.FinancistoScheduleOneWorkManager
import ru.orangesoftware.financisto.service.RecurrenceScheduler

private const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
private const val SCHEDULED_BACKUP = "ru.orangesoftware.financisto.SCHEDULED_BACKUP"

private const val TAG = "ScheduledAlarmReceiver"

class ScheduledAlarmReceiver : PackageReplaceReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "Received ${intent?.action}")
        if (context == null || intent == null) return

        when(intent.action) {
            BOOT_COMPLETED -> {
                requestScheduleAll(context)
                requestScheduleAutoBackup(context)
            }
            SCHEDULED_BACKUP -> {
                requestAutoBackup(context)
            }
            else -> {
                requestScheduleOne(context, intent)
            }
        }
    }

    private fun requestScheduleOne(context: Context, intent: Intent) =
        WorkManager.getInstance(context)
            .enqueue(
                OneTimeWorkRequestBuilder<FinancistoScheduleOneWorkManager>()
                    .setInputData(intent.toInputData())
                    .build()
            )

    private fun Intent.toInputData(): Data = Data.Builder()
        .putLong(
            RecurrenceScheduler.SCHEDULED_TRANSACTION_ID,
            this.getLongExtra(RecurrenceScheduler.SCHEDULED_TRANSACTION_ID, -1)
        )
        .build()


    private fun requestAutoBackup(context: Context) = WorkManager.getInstance(context)
        .enqueue(OneTimeWorkRequestBuilder<FinancistoAutoBackupWorkManager>().build())
}
