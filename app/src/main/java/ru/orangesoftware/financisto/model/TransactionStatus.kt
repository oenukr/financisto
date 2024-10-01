package ru.orangesoftware.financisto.model

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.LocalizableEnum

enum class TransactionStatus(
    override val titleId: Int,
    val iconId: Int,
    val colorId: Int,
) : LocalizableEnum {
    RS(
        R.string.transaction_status_restored,
        R.drawable.transaction_status_restored_2,
        R.color.restored_transaction_color
    ),
    PN(
        R.string.transaction_status_pending,
        R.drawable.transaction_status_pending_2,
        R.color.pending_transaction_color
    ),
    UR(
        R.string.transaction_status_unreconciled,
        R.drawable.transaction_status_unreconciled_2,
        R.color.unreconciled_transaction_color
    ),
    CL(
        R.string.transaction_status_cleared,
        R.drawable.transaction_status_cleared_2,
        R.color.cleared_transaction_color
    ),
    RC(
        R.string.transaction_status_reconciled,
        R.drawable.transaction_status_reconciled_2,
        R.color.reconciled_transaction_color
    );
}
