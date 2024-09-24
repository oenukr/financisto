package ru.orangesoftware.financisto.activity;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import ru.orangesoftware.financisto.service.FinancistoService;
import ru.orangesoftware.financisto.service.RecurrenceScheduler;

private const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
private const val SCHEDULED_BACKUP = "ru.orangesoftware.financisto.SCHEDULED_BACKUP"

class ScheduledAlarmReceiver : PackageReplaceReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("ScheduledAlarmReceiver", "Received ${intent?.action}")
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

    private fun requestScheduleOne(context: Context, intent: Intent) {
        val serviceIntent = Intent(
            FinancistoService.ACTION_SCHEDULE_ONE,
            null,
            context,
            FinancistoService::class.java
        )
        serviceIntent.putExtra(
            RecurrenceScheduler.SCHEDULED_TRANSACTION_ID,
            intent.getLongExtra(RecurrenceScheduler.SCHEDULED_TRANSACTION_ID, -1)
        )
        FinancistoService.enqueueWork(context, serviceIntent)
    }

    private fun requestAutoBackup(context: Context) {
        val serviceIntent = Intent(FinancistoService.ACTION_AUTO_BACKUP, null, context, FinancistoService::class.java)
        FinancistoService.enqueueWork(context, serviceIntent)
    }

}
