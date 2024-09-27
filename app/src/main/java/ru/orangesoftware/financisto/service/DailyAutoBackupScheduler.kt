package ru.orangesoftware.financisto.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import ru.orangesoftware.financisto.activity.ScheduledAlarmReceiver
import ru.orangesoftware.financisto.utils.MyPreferences
import java.util.Calendar
import java.util.Date

class DailyAutoBackupScheduler(
    private val hh: Int,
    private val mm: Int,
    private val now: Long
) {

    companion object {
        @JvmStatic
        fun scheduleNextAutoBackup(context: Context) {
            if (MyPreferences.isAutoBackupEnabled(context)) {
                val hhmm: Int = MyPreferences.getAutoBackupTime(context)
                val hh: Int = hhmm / 100
                val mm: Int = hhmm - 100 * hh
                DailyAutoBackupScheduler(hh, mm, System.currentTimeMillis()).scheduleBackup(context)
            }
        }
    }

    private fun scheduleBackup(context: Context) {
        val service: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent: PendingIntent = createPendingIntent(context)
        val scheduledTime: Date = getScheduledTime()
        service.set(AlarmManager.RTC_WAKEUP, scheduledTime.time, pendingIntent)
        Log.i("Financisto", "Next auto-backup scheduled at $scheduledTime")
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent: Intent = Intent("ru.orangesoftware.financisto.SCHEDULED_BACKUP")
        intent.setClass(context, ScheduledAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
                context,
                -100,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    protected fun getScheduledTime(): Date {
        val c: Calendar = Calendar.getInstance()
        c.setTimeInMillis(now)
        c.set(Calendar.HOUR_OF_DAY, hh)
        c.set(Calendar.MINUTE, mm)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.getTimeInMillis() < now) {
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.time
    }

}
