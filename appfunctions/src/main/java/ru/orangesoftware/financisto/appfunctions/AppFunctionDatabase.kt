// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.appfunctions

import android.app.PendingIntent
import android.net.Uri
import java.time.LocalDateTime

data class AccountInfo(
    val id: Long,
    val title: String,
    val currencyCode: String = "",
    val type: String = "",
    val lastCategoryId: Long = 0L,
    val currencyId: Long = 0L,
)

data class CategoryInfo(
    val id: Long,
    val title: String,
    val fullPath: String = title,
    val isIncome: Boolean = false,
    val parentId: Long = 0L,
)

data class PayeeInfo(
    val id: Long,
    val title: String,
    val lastCategoryId: Long = 0L,
)

data class TransactionSplit(
    val categoryId: Long,
    val amount: Long,
    val note: String = "",
)

data class HistoricalCategorySignal(
    val categoryId: Long,
    val payeeId: Long = 0L,
    val note: String = "",
)

data class AppFunctionTransactionDraft(
    val draftId: String,
    val fromAccountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val payeeId: Long?,
    val payeeName: String?,
    val amount: Long,
    val toAmount: Long?,
    val isIncome: Boolean,
    val note: String,
    val dateTime: Long,
    val attachedPicture: String? = null,
    val splits: List<TransactionSplit> = emptyList(),
    val receiptAttachFailed: Boolean = false,
)

interface AppFunctionDatabase {
    fun getAccounts(): List<AccountInfo>
    fun getCategories(): List<CategoryInfo>
    fun getPayees(): List<PayeeInfo>
    fun getCategoryHistory(limit: Int = DEFAULT_HISTORY_LIMIT): List<HistoricalCategorySignal> = emptyList()
    fun saveReceiptPicture(uri: Uri): String? = null
    fun extractReceiptImageDateTime(uri: Uri): LocalDateTime? = null
    fun createTransactionReviewPendingIntent(draft: AppFunctionTransactionDraft): PendingIntent

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 500
    }
}
