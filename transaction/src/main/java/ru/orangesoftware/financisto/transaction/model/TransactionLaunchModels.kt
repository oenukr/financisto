// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class TransactionMode {
    EXPENSE,
    INCOME,
    TRANSFER,
}

enum class TransactionStatusOption(val label: String) {
    RS("Restored"),
    PN("Pending"),
    UR("Unreconciled"),
    CLEARED("Cleared"),
    RECONCILED("Reconciled"),
}

@Parcelize
data class DraftSplit(
    val categoryId: Long,
    val categoryName: String? = null,
    val amountMinor: Long,
    val note: String = "",
    val projectId: Long = 0L,
) : Parcelable

@Parcelize
data class TransactionDraft(
    val draftId: String,
    val mode: TransactionMode,
    val amountMinor: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val originalCurrencyId: Long? = null,
    val originalAmountMinor: Long? = null,
    val toAmountMinor: Long? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    val pendingPayeeName: String? = null,
    val projectId: Long = 0L,
    val locationId: Long = 0L,
    val dateTimeMillis: Long,
    val status: TransactionStatusOption = TransactionStatusOption.UR,
    val note: String = "",
    val isCCardPayment: Boolean = false,
    val stagedReceiptFileName: String? = null,
    val splits: List<DraftSplit> = emptyList(),
) : Parcelable

@Parcelize
sealed interface TransactionLaunchArgs : Parcelable {
    @Parcelize
    data class BlankNew(
        val accountId: Long? = null,
        val initialMode: TransactionMode = TransactionMode.EXPENSE,
        val initialDateTimeMillis: Long? = null,
    ) : TransactionLaunchArgs

    @Parcelize
    data class Edit(
        val transactionId: Long,
    ) : TransactionLaunchArgs

    @Parcelize
    data class AiDraft(
        val draft: TransactionDraft,
    ) : TransactionLaunchArgs
}
