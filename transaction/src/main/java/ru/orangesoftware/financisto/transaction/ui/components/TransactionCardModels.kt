// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import ru.orangesoftware.financisto.transaction.R
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
)

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
