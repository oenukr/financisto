package ru.orangesoftware.financisto.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.recur.NotificationOptions
import ru.orangesoftware.financisto.utils.NotificationChannels

interface NotificationPresentation {
    fun generateNotification(
        context: Context,
        transactionInfo: TransactionInfo,
        tickerText: String,
        contentTitle: String,
        text: String,
    ): Notification {
        val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.TRANSACTIONS)
            .setContentIntent(contentIntent(context, transactionInfo))
            .setSmallIcon(transactionInfo.notificationIcon)
            .setWhen(System.currentTimeMillis())
            .setTicker(tickerText)
            .setContentText(text)
            .setContentTitle(contentTitle)
            .setAutoCancel(true)
            .build()

        applyNotificationOptions(notification, transactionInfo.notificationOptions)

        return notification
    }

    /* https://stackoverflow.com/a/3730394/365675 */
    private fun contentIntent(context: Context, transactionInfo: TransactionInfo): PendingIntent =
        PendingIntent.getActivity(
            context,
            transactionInfo.id.toInt(),
            Intent(
                context,
                transactionInfo.activity
            ).apply { putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, transactionInfo.id) },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun applyNotificationOptions(notification: Notification, notificationOptions: String?) {
        if (notificationOptions == null) {
            notification.defaults = Notification.DEFAULT_ALL
        } else {
            val options = NotificationOptions.parse(notificationOptions)
            options.apply(notification)
        }
    }
}
