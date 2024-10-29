package ru.orangesoftware.financisto.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ru.orangesoftware.financisto.service.FinancistoScheduleAllWorkManager
import ru.orangesoftware.financisto.service.FinancistoScheduleAutoBackupWorkManager

private const val PACKAGE_REPLACED = "android.intent.action.PACKAGE_REPLACED"

private const val TAG = "PackageReplaceReceiver"

open class PackageReplaceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val dataString = intent.dataString

        if (PACKAGE_REPLACED == action) {
            Log.d(TAG, "Received $dataString")
            if ("package:ru.orangesoftware.financisto" == dataString) {
                Log.d(TAG, "Re-scheduling all transactions")
                requestScheduleAll(context)
                requestScheduleAutoBackup(context)
            }
        }
    }

    protected fun requestScheduleAll(context: Context) = WorkManager.getInstance(context)
        .enqueue(OneTimeWorkRequestBuilder<FinancistoScheduleAllWorkManager>().build())

    protected fun requestScheduleAutoBackup(context: Context) = WorkManager.getInstance(context)
        .enqueue(OneTimeWorkRequestBuilder<FinancistoScheduleAutoBackupWorkManager>().build())
}
