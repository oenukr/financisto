package ru.orangesoftware.financisto.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.utils.MyPreferences

class FinancistoSmsWorkManager(
    context: Context,
    workerParams: WorkerParameters,
): Worker(context, workerParams), NotificationPresentation {
    private val logger = DependenciesHolder().logger

    private val db = DatabaseAdapter(context).also { it.open() }
    private val smsProcessor = SmsTransactionProcessor(db)

    override fun onStopped() {
        db.close()
        super.onStopped()
    }

    override fun doWork(): Result {
        inputData.getString("action")?.let { action ->
            when (action) {
                ACTION_NEW_TRANSACTION_SMS -> processSmsTransaction()
            }
        }
        return Result.success()
    }

    private fun processSmsTransaction() {
        val number = inputData.getString(SmsReceiver.SMS_TRANSACTION_NUMBER) ?: return
        val body = inputData.getString(SmsReceiver.SMS_TRANSACTION_BODY) ?: return
        smsProcessor.createTransactionBySms(
            number,
            body,
            MyPreferences.getSmsTransactionStatus(applicationContext),
            MyPreferences.shouldSaveSmsToTransactionNote(applicationContext),
        )?.let { transaction ->
            db.getTransactionInfo(transaction.id)?.let { transactionInfo ->
                notifyUser(
                    createSmsTransactionNotification(transactionInfo, number),
                    transaction.id.toInt(),
                )
                AccountWidget.updateWidgets(applicationContext)
            } ?: logger.e("Transaction info does not exist for ${transaction.id}")
        }
    }

    private fun createSmsTransactionNotification(transactionInfo: TransactionInfo, number: String): Notification {
        val tickerText = ContextCompat.getString(
            applicationContext,
            R.string.new_sms_transaction_text
        ).format(number)
        val contentTitle = ContextCompat.getString(
            applicationContext,
            R.string.new_sms_transaction_title
        ).format(number)
        val text = transactionInfo.getNotificationContentText(applicationContext)

        return generateNotification(applicationContext, transactionInfo, tickerText, contentTitle, text)
    }

    private fun notifyUser(notification: Notification, id: Int) = getSystemService(
        applicationContext,
        NotificationManager::class.java,
    )?.notify(id, notification)

    companion object {
        const val ACTION_NEW_TRANSACTION_SMS: String = "ru.orangesoftware.financisto.NEW_TRANSACTON_SMS"

        @JvmStatic
        fun enqueueWork(context: Context, workRequest: WorkRequest) {
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
