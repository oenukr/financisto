// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object TransactionIds {
    const val UNSAVED_TRANSACTION_ID: Long = -1L
    const val NO_CATEGORY_ID: Long = 0L
    const val SPLIT_CATEGORY_ID: Long = -1L
    const val NO_PROJECT_ID: Long = 0L
    const val CURRENT_LOCATION_ID: Long = 0L
    const val NO_PAYEE_ID: Long = 0L
}

data class AccountOption(
    val id: Long,
    val title: String,
    val typeLabel: String,
    val currencyId: Long,
    val currencyCode: String,
    val currencySymbol: String,
    val currencyDecimals: Int,
    val balanceMinor: Long,
    val lastCategoryId: Long = 0L,
    val lastAccountId: Long = 0L,
    val isActive: Boolean = true,
)

data class CurrencyOption(
    val id: Long,
    val code: String,
    val symbol: String,
    val decimals: Int,
    val isDefault: Boolean = false,
)

data class CategoryOption(
    val id: Long,
    val parentId: Long,
    val title: String,
    val fullPath: String,
    val level: Int,
    val isIncome: Boolean,
    val lastLocationId: Long = 0L,
    val lastProjectId: Long = 0L,
)

data class PayeeOption(
    val id: Long,
    val title: String,
    val lastCategoryId: Long = 0L,
    val isActive: Boolean = true,
)

data class ProjectOption(
    val id: Long,
    val title: String,
    val isActive: Boolean = true,
)

data class LocationOption(
    val id: Long,
    val title: String,
    val isActive: Boolean = true,
)

@Parcelize
data class EditableSplit(
    val localId: Long,
    val categoryId: Long,
    val amountMinor: Long,
    val isIncome: Boolean = false,
    val note: String = "",
    val projectId: Long = TransactionIds.NO_PROJECT_ID,
) : Parcelable

data class TransactionFormPreferences(
    val isRememberAccount: Boolean = true,
    val isRememberCategory: Boolean = false,
    val isRememberLocation: Boolean = false,
    val isRememberProject: Boolean = false,
    val isShowPayee: Boolean = true,
    val isShowPayeeInTransfers: Boolean = false,
    val isShowCurrency: Boolean = true,
    val isShowNote: Boolean = true,
    val isShowTakePicture: Boolean = true,
    val isShowIsCCardPayment: Boolean = true,
    val isSetFocusOnAmountField: Boolean = true,
    val isPinProtected: Boolean = false,
    val isPinProtectedNewTransaction: Boolean = true,
    val is24HourFormat: Boolean = true,
    val lastAccountId: Long = -1L,
)

data class TransactionCatalog(
    val accounts: List<AccountOption> = emptyList(),
    val currencies: List<CurrencyOption> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    val payees: List<PayeeOption> = emptyList(),
    val projects: List<ProjectOption> = emptyList(),
    val locations: List<LocationOption> = emptyList(),
    val recentNotes: List<String> = emptyList(),
)

data class LoadedTransactionRecord(
    val id: Long,
    val mode: TransactionMode,
    val fromAccountId: Long,
    val toAccountId: Long,
    val categoryId: Long,
    val payeeId: Long,
    val projectId: Long,
    val locationId: Long,
    val fromAmountMinor: Long,
    val toAmountMinor: Long,
    val originalCurrencyId: Long,
    val originalFromAmountMinor: Long,
    val note: String,
    val dateTimeMillis: Long,
    val status: TransactionStatusOption,
    val attachedPicture: String?,
    val isCCardPayment: Boolean,
    val splits: List<EditableSplit>,
)

sealed interface TransactionEditLoadResult {
    data class Loaded(val record: LoadedTransactionRecord) : TransactionEditLoadResult
    data class RequiresLegacyFallback(val transactionId: Long, val isTransfer: Boolean) : TransactionEditLoadResult
    data class NotFound(val transactionId: Long) : TransactionEditLoadResult
}

data class SaveSplitCommand(
    val categoryId: Long,
    val amountMinor: Long,
    val note: String,
    val projectId: Long,
)

data class SaveTransactionCommand(
    val existingTransactionId: Long,
    val mode: TransactionMode,
    val fromAccountId: Long,
    val toAccountId: Long,
    val categoryId: Long,
    val payeeId: Long,
    val pendingPayeeName: String?,
    val projectId: Long,
    val pendingProjectName: String?,
    val locationId: Long,
    val fromAmountMinor: Long,
    val toAmountMinor: Long,
    val originalCurrencyId: Long,
    val originalFromAmountMinor: Long,
    val note: String,
    val dateTimeMillis: Long,
    val status: TransactionStatusOption,
    val attachedPicture: String?,
    val isCCardPayment: Boolean,
    val splits: List<SaveSplitCommand>,
)
