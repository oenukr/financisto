package ru.orangesoftware.financisto.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.orangesoftware.financisto.service.DailyAutoBackupScheduler.Companion.scheduleNextAutoBackup

class FinancistoScheduleAutoBackupWorkManager(
    context: Context,
    workerParams: WorkerParameters,
): Worker(context, workerParams) {
    override fun doWork(): Result {
        scheduleNextAutoBackup(applicationContext)

        return Result.success()
    }
}
