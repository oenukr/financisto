package ru.orangesoftware.financisto.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import ru.orangesoftware.financisto.service.FinancistoService

private const val PACKAGE_REPLACED = "android.intent.action.PACKAGE_REPLACED";

open class PackageReplaceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val dataString = intent.dataString

        if (PACKAGE_REPLACED == action) {
            Log.d("PackageReplaceReceiver", "Received $dataString")
            if ("package:ru.orangesoftware.financisto" == dataString) {
                Log.d("PackageReplaceReceiver", "Re-scheduling all transactions")
                requestScheduleAll(context)
                requestScheduleAutoBackup(context)
            }
        }
    }

    protected fun requestScheduleAll(context: Context) {
        val serviceIntent = Intent(
            FinancistoService.ACTION_SCHEDULE_ALL,
            null,
            context,
            FinancistoService::class.java
        )
        FinancistoService.enqueueWork(context, serviceIntent)
    }

    protected fun requestScheduleAutoBackup(context: Context) {
        val serviceIntent = Intent(
            FinancistoService.ACTION_SCHEDULE_AUTO_BACKUP,
            null,
            context,
            FinancistoService::class.java
        )
        FinancistoService.enqueueWork(context, serviceIntent)
    }
}
