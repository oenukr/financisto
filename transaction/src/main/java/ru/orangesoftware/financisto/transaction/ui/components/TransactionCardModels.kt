// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import kotlinx.parcelize.Parcelize
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption

@Immutable
data class HeroAmountDisplayModel(
    val mode: TransactionMode,
    val isDualCurrency: Boolean,
    val primaryInput: String,
    val primaryCurrencyCode: String,
    val primaryCurrencySymbol: String,
    val primaryAccountLabel: String,
    val secondaryInput: String,
    val secondaryCurrencyCode: String,
    val secondaryCurrencySymbol: String,
    val secondaryAccountLabel: String,
    val enabled: Boolean = true,
)

@Immutable
@Parcelize
data class SplitEditorDraft(
    val splitLocalId: Long,
    val amountText: String,
    val noteText: String,
    val isIncome: Boolean,
    val syncedAmountMinor: Long,
    val syncedNote: String,
    val syncedIsIncome: Boolean,
    val syncedCategoryId: Long,
    val syncedCurrencyDecimals: Int,
    val forceSyncAmount: Boolean = false,
    val forceSyncIsIncome: Boolean = false,
) : Parcelable {
    companion object {
        fun fromSplit(split: EditableSplit, currencyDecimals: Int): SplitEditorDraft = SplitEditorDraft(
            splitLocalId = split.localId,
            amountText = AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals),
            noteText = split.note,
            isIncome = split.isIncome,
            syncedAmountMinor = split.amountMinor,
            syncedNote = split.note,
            syncedIsIncome = split.isIncome,
            syncedCategoryId = split.categoryId,
            syncedCurrencyDecimals = currencyDecimals,
        )

        fun resolve(
            split: EditableSplit,
            currencyDecimals: Int,
            cachedDraft: SplitEditorDraft?,
        ): SplitEditorDraft {
            if (cachedDraft == null || cachedDraft.splitLocalId != split.localId) {
                return fromSplit(split, currencyDecimals)
            }
            val amountChanged = cachedDraft.forceSyncAmount ||
                split.amountMinor != cachedDraft.syncedAmountMinor ||
                currencyDecimals != cachedDraft.syncedCurrencyDecimals
            val noteChanged = split.note != cachedDraft.syncedNote
            val incomeChanged = cachedDraft.forceSyncIsIncome ||
                split.isIncome != cachedDraft.syncedIsIncome ||
                split.categoryId != cachedDraft.syncedCategoryId

            return cachedDraft.copy(
                amountText = if (amountChanged) {
                    AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals)
                } else {
                    cachedDraft.amountText
                },
                noteText = if (noteChanged) split.note else cachedDraft.noteText,
                isIncome = if (incomeChanged) split.isIncome else cachedDraft.isIncome,
                syncedAmountMinor = split.amountMinor,
                syncedNote = split.note,
                syncedIsIncome = split.isIncome,
                syncedCategoryId = split.categoryId,
                syncedCurrencyDecimals = currencyDecimals,
                forceSyncAmount = false,
                forceSyncIsIncome = false,
            )
        }
    }
}

@Immutable
data class SplitCategoryCallbacks(
    val onExitSplitMode: () -> Unit,
    val onCreateCategoryClick: () -> Unit,
    val onAddSplitClick: () -> Unit,
    val onEditSplitClick: (Long) -> Unit,
    val onRemoveSplitClick: (Long) -> Unit,
    val onAdjustTotal: () -> Unit,
    val onDistributeEvenly: () -> Unit,
    val onAdjustLast: () -> Unit,
    val onCollapseSingleSplit: () -> Unit,
)

@Composable
fun TransactionStatusOption.localizedLabel(): String {
    val resId = when (this) {
        TransactionStatusOption.RS -> R.string.tx_status_restored
        TransactionStatusOption.PN -> R.string.tx_status_pending
        TransactionStatusOption.UR -> R.string.tx_status_unreconciled
        TransactionStatusOption.CLEARED -> R.string.tx_status_cleared
        TransactionStatusOption.RECONCILED -> R.string.tx_status_reconciled
    }
    return stringResource(resId)
}
