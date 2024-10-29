package ru.orangesoftware.financisto.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.TransactionInfo

class FinancistoScheduleOneWorkManager(
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
        inputData.getLong(RecurrenceScheduler.SCHEDULED_TRANSACTION_ID, -1)
            .takeIf { it > 0 }
            ?.also { transactionId ->
                scheduler.scheduleOne(applicationContext, transactionId)
                    ?.also { transactionInfo ->
                        notifyUser(transactionInfo)
                        AccountWidget.updateWidgets(applicationContext)
                    }
            }

        return Result.success()
    }

    private fun notifyUser(id: Int, notification: Notification) = getSystemService(
        applicationContext,
        NotificationManager::class.java
    )?.notify(id, notification)

    private fun notifyUser(transactionInfo: TransactionInfo) =
        notifyUser(transactionInfo.id.toInt(), transactionInfo.toNotification())

    private fun TransactionInfo.toNotification(): Notification {
        val tickerText = getNotificationTickerText(applicationContext)
        val contentTitle = getNotificationContentTitle(applicationContext)
        val text = getNotificationContentText(applicationContext)

        return generateNotification(applicationContext, this, tickerText, contentTitle, text)
    }
}
