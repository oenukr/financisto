package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus

class BlotterOperations(
    private val activity: BlotterActivity,
    private val db: DatabaseAdapter,
    transactionId: Long,
) {
    private val originalTransaction: Transaction = db.getTransaction(transactionId)
    private val targetTransaction: Transaction = if (originalTransaction.isSplitChild) {
        db.getTransaction(originalTransaction.parentId)
    } else {
        originalTransaction
    }

    private var newFromTemplate: Boolean = false

    fun asNewFromTemplate(): BlotterOperations = this.apply { newFromTemplate = true }

    fun editTransaction() {
        if (targetTransaction.isTransfer) {
            startEditTransactionActivity(TransferActivity::class.java, EDIT_TRANSFER_REQUEST)
        } else {
            startEditTransactionActivity(TransactionActivity::class.java, EDIT_TRANSACTION_REQUEST)
        }
    }

    private fun startEditTransactionActivity(activityClass: Class<out Activity>, requestCode: Int) {
        val intent = Intent(activity, activityClass)
        intent.putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, targetTransaction.id)
        intent.putExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, false)
        intent.putExtra(AbstractTransactionActivity.NEW_FROM_TEMPLATE_EXTRA, newFromTemplate)
        activity.startActivityForResult(intent, requestCode)
    }

    fun deleteTransaction() {
        val titleId = if (targetTransaction.isTemplate()) {
            R.string.delete_template_confirm
        } else {
            if (originalTransaction.isSplitChild) {
                R.string.delete_transaction_parent_confirm
            } else {
                R.string.delete_transaction_confirm
            }
        }
        AlertDialog.Builder(activity)
            .setMessage(titleId)
            .setPositiveButton(R.string.yes) { _, _ ->
                val transactionIdToDelete = targetTransaction.id
                db.deleteTransaction(transactionIdToDelete)
                activity.afterDeletingTransaction(transactionIdToDelete)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    fun duplicateTransaction(multiplier: Int): Long = if (multiplier > 1) {
        db.duplicateTransactionWithMultiplier(targetTransaction.id, multiplier)
    } else {
        db.duplicateTransaction(targetTransaction.id)
    }

    fun duplicateAsTemplate() {
        db.duplicateTransactionAsTemplate(targetTransaction.id)
    }

    fun clearTransaction() {
        db.updateTransactionStatus(targetTransaction.id, TransactionStatus.CLEARED)
    }

    fun reconcileTransaction() {
        db.updateTransactionStatus(targetTransaction.id, TransactionStatus.RECONCILED)
    }

    companion object {
        private const val EDIT_TRANSACTION_REQUEST: Int = 2
        private const val EDIT_TRANSFER_REQUEST: Int = 4
    }
}
