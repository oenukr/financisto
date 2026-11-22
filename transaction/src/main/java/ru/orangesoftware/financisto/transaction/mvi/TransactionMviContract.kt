// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption

@Parcelize
data class TransactionFormState(
    val existingTransactionId: Long = TransactionIds.UNSAVED_TRANSACTION_ID,
    val aiDraftId: String? = null,
    val mode: TransactionMode = TransactionMode.EXPENSE,
    val dateTimeMillis: Long = System.currentTimeMillis(),
    val status: TransactionStatusOption = TransactionStatusOption.UR,
    val fromAccountId: Long = 0L,
    val toAccountId: Long = 0L,
    val originalCurrencyId: Long? = null,
    val primaryAmountInput: String = "0",
    val primaryAmountMinor: Long = 0L,
    val secondaryAmountInput: String = "0",
    val secondaryAmountMinor: Long = 0L,
    val exchangeRateInput: String = "",
    val exchangeRate: Double = 0.0,
    val payeeId: Long = TransactionIds.NO_PAYEE_ID,
    val pendingPayeeName: String? = null,
    val payeeQuery: String = "",
    val categoryId: Long = TransactionIds.NO_CATEGORY_ID,
    val isSplitMode: Boolean = false,
    val splits: List<EditableSplit> = emptyList(),
    val nextSplitLocalId: Long = -1L,
    val locationId: Long = TransactionIds.CURRENT_LOCATION_ID,
    val locationQuery: String = "",
    val note: String = "",
    val projectId: Long = TransactionIds.NO_PROJECT_ID,
    val pendingProjectName: String? = null,
    val attachedPictureFileName: String? = null,
    val initialPersistedPictureFileName: String? = null,
    val newlyStagedPictureFiles: List<String> = emptyList(),
    val isCCardPayment: Boolean = false,
    val isFromNewBlankLaunch: Boolean = true,
) : Parcelable {
    val isEditing: Boolean get() = existingTransactionId > 0L
    val isAiDraft: Boolean get() = aiDraftId != null
}

enum class ExpandedInlineField {
    PAYEE,
    CATEGORY,
    LOCATION,
}

enum class CalculatorTarget {
    PRIMARY_AMOUNT,
    SECONDARY_AMOUNT,
    EXCHANGE_RATE,
    SPLIT_AMOUNT,
}

sealed interface ActiveSheet {
    data object DatePicker : ActiveSheet
    data object TimePicker : ActiveSheet
    data object StatusPicker : ActiveSheet
    data class AccountPicker(val isDestination: Boolean) : ActiveSheet
    data object CurrencyPicker : ActiveSheet
    data class CategoryBrowser(val forSplitLocalId: Long?) : ActiveSheet
    data class SplitItemEditor(val split: EditableSplit, val isNew: Boolean) : ActiveSheet
    data class ProjectPicker(val forSplitLocalId: Long?) : ActiveSheet
    data class Calculator(
        val target: CalculatorTarget,
        val initialText: String,
        val splitLocalId: Long? = null,
    ) : ActiveSheet
    data object DiscardConfirmation : ActiveSheet
    data class ConsumedDraftPrompt(val savedTransactionId: Long) : ActiveSheet
}

sealed interface TransactionUiState {
    data object Loading : TransactionUiState
    data object PinLockedMask : TransactionUiState
    data class NotFound(val transactionId: Long) : TransactionUiState
    data object NoAccounts : TransactionUiState
    data object LegacyRedirecting : TransactionUiState

    data class Ready(
        val form: TransactionFormState,
        val initialFormSnapshot: TransactionFormState,
        val catalog: TransactionCatalog,
        val prefs: TransactionFormPreferences,
        val activeSheet: ActiveSheet? = null,
        val expandedField: ExpandedInlineField? = null,
        val validationErrors: List<String> = emptyList(),
        val isSaving: Boolean = false,
        val isFetchingRate: Boolean = false,
    ) : TransactionUiState {
        val hasUnsavedChanges: Boolean
            get() = form != initialFormSnapshot ||
                (form.isAiDraft && form.attachedPictureFileName != null)
    }
}

sealed interface TransactionIntent {
    data class SwitchMode(val mode: TransactionMode) : TransactionIntent
    data object CycleSignOrMode : TransactionIntent
    data class UpdatePrimaryAmount(val text: String) : TransactionIntent
    data class UpdateSecondaryAmount(val text: String) : TransactionIntent
    data class UpdateExchangeRate(val text: String) : TransactionIntent
    data object FetchLiveRate : TransactionIntent
    data class OpenCalculator(val target: CalculatorTarget, val splitLocalId: Long? = null) : TransactionIntent
    data class ApplyCalculatorResult(
        val target: CalculatorTarget,
        val value: Double,
        val splitLocalId: Long? = null,
    ) : TransactionIntent

    data class OpenAccountPicker(val isDestination: Boolean) : TransactionIntent
    data class SelectAccount(val accountId: Long, val isDestination: Boolean) : TransactionIntent
    data object SwapTransferAccounts : TransactionIntent

    data object OpenCurrencyPicker : TransactionIntent
    data class SelectCurrency(val currencyId: Long?) : TransactionIntent

    data class ToggleInlineField(val field: ExpandedInlineField) : TransactionIntent
    data object CollapseInlineField : TransactionIntent

    data class UpdatePayeeQuery(val query: String) : TransactionIntent
    data class SelectPayee(val payeeId: Long) : TransactionIntent
    data class CreatePendingPayee(val name: String) : TransactionIntent
    data object ClearPayee : TransactionIntent

    data class SelectCategory(val categoryId: Long, val forSplitLocalId: Long? = null) : TransactionIntent
    data object ClearCategory : TransactionIntent
    data object ToggleSplitMode : TransactionIntent
    data object OpenNewSplitEditor : TransactionIntent
    data class OpenEditSplitEditor(val localId: Long) : TransactionIntent
    data class SaveSplitItem(val split: EditableSplit) : TransactionIntent
    data class RemoveSplitItem(val localId: Long) : TransactionIntent
    data object RebalanceAdjustTotal : TransactionIntent
    data object RebalanceDistributeEvenly : TransactionIntent
    data object RebalanceAdjustLast : TransactionIntent
    data object CollapseSingleSplit : TransactionIntent

    data class UpdateLocationQuery(val query: String) : TransactionIntent
    data class SelectLocation(val locationId: Long) : TransactionIntent
    data object ClearLocation : TransactionIntent
    data object OpenExternalLocationCreator : TransactionIntent
    data object OpenExternalCategoryCreator : TransactionIntent
    data class ExternalEntityCreated(val kind: ExternalEntityKind, val createdId: Long) : TransactionIntent

    data class UpdateNote(val text: String) : TransactionIntent
    data class SelectRecentNote(val text: String) : TransactionIntent
    data object ClearNote : TransactionIntent

    data class OpenProjectPicker(val forSplitLocalId: Long? = null) : TransactionIntent
    data class SelectProject(val projectId: Long, val forSplitLocalId: Long? = null) : TransactionIntent
    data class CreatePendingProject(val name: String, val forSplitLocalId: Long? = null) : TransactionIntent
    data object ClearProject : TransactionIntent

    data class OpenCategoryBrowser(val forSplitLocalId: Long? = null) : TransactionIntent
    data object OpenDatePicker : TransactionIntent
    data object OpenTimePicker : TransactionIntent
    data class UpdateDate(val year: Int, val monthZeroBased: Int, val dayOfMonth: Int) : TransactionIntent
    data class UpdateTime(val hourOfDay: Int, val minute: Int) : TransactionIntent
    data object OpenStatusPicker : TransactionIntent
    data class SelectStatus(val status: TransactionStatusOption) : TransactionIntent
    data class ToggleCCardPayment(val checked: Boolean) : TransactionIntent

    data object RequestCameraPhoto : TransactionIntent
    data object RequestGalleryPhoto : TransactionIntent
    data class AttachReceiptFromUri(val uriString: String) : TransactionIntent
    data class AttachCapturedPhotoFile(val fileName: String) : TransactionIntent
    data object RemoveReceipt : TransactionIntent

    data object ClearFormClicked : TransactionIntent
    data object OpenInLegacyClicked : TransactionIntent
    data object SaveClicked : TransactionIntent
    data object SaveAndNewClicked : TransactionIntent
    data object CancelOrBackClicked : TransactionIntent
    data object ConfirmDiscard : TransactionIntent
    data object DismissSheet : TransactionIntent
    data class OpenSavedTransaction(val transactionId: Long) : TransactionIntent
    data object DuplicateDraftAnyway : TransactionIntent
}

enum class ExternalEntityKind {
    CATEGORY,
    LOCATION,
}

sealed interface TransactionEffect {
    data class SavedAndFinish(val transactionId: Long) : TransactionEffect
    data object CloseCancelled : TransactionEffect
    data class OpenLegacyEditor(val args: TransactionLaunchArgs) : TransactionEffect
    data object LaunchExternalCategoryCreator : TransactionEffect
    data object LaunchExternalLocationCreator : TransactionEffect
    data object LaunchCameraCapture : TransactionEffect
    data object LaunchGalleryPick : TransactionEffect
    data class ShowMessage(val message: String) : TransactionEffect
}
