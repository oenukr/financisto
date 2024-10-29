package ru.orangesoftware.financisto.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getString
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.MassOpActivity
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.utils.NotificationChannels

class FinancistoScheduleAllWorkManager(
    context: Context,
    workerParams: WorkerParameters,
): Worker(context, workerParams), NotificationPresentation {
    private val db = DatabaseAdapter(context).also { it.open() }
    private val scheduler = RecurrenceScheduler(db)

    override fun onStopped() {
        db.close()
        super.onStopped()
    }

    override fun doWork(): Result {
        scheduler.scheduleAll(applicationContext)
            .takeIf { it > 0 }
            ?.also { notifyUser(createRestoredNotification(it), RESTORED_NOTIFICATION_ID) }

        return Result.success()
    }

    private fun notifyUser(notification: Notification, id: Int) = getSystemService(
        applicationContext,
        NotificationManager::class.java,
    )?.notify(id, notification)


    private fun createRestoredNotification(count: Int): Notification {
        val time = System.currentTimeMillis()
        val text: String = getString(applicationContext, R.string.scheduled_transactions_have_been_restored).format(count)
        val contentTitle: String = getString(applicationContext, R.string.scheduled_transactions_restored)

        val contentIntent = contentIntent()

        return NotificationCompat.Builder(applicationContext, NotificationChannels.RESTORED)
            .setContentIntent(contentIntent)
            .setSmallIcon(R.drawable.notification_icon_transaction)
            .setWhen(time)
            .setTicker(text)
            .setContentText(text)
            .setContentTitle(contentTitle)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
    }

    private fun contentIntent(): PendingIntent? = PendingIntent.getActivity(
        applicationContext,
        0,
        createIntent(),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createIntent() =
        Intent(applicationContext, MassOpActivity::class.java).also { intent ->
                WhereFilter("").eq(BlotterFilter.STATUS, TransactionStatus.RS.name)
                    .also { filter -> filter.toIntent(intent) }
            }

    companion object {
        const val RESTORED_NOTIFICATION_ID: Int = 0
    }
}
