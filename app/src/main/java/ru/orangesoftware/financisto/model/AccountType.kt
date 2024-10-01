package ru.orangesoftware.financisto.model

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.EntityEnum

enum class AccountType(
    override val titleId: Int,
    override val iconId: Int,
    val hasIssuer: Boolean,
    val hasNumber: Boolean,
    val isCard: Boolean,
    val isCreditCard: Boolean,
    val isElectronic: Boolean,
) : EntityEnum {

    CASH(R.string.account_type_cash, R.drawable.account_type_cash, false, false, false, false, false),
    BANK(R.string.account_type_bank, R.drawable.account_type_bank, true, false, false, false, false),
    DEBIT_CARD(R.string.account_type_debit_card, R.drawable.account_type_card, true, true, true, false, false),
    CREDIT_CARD(R.string.account_type_credit_card, R.drawable.account_type_card, true, true, true, true, false),
    ELECTRONIC(R.string.account_type_electronic, R.drawable.account_type_electronic, false, false, false, false, true),
    ASSET(R.string.account_type_asset, R.drawable.account_type_asset, false, false, false, false, false),
    LIABILITY(R.string.account_type_liability, R.drawable.account_type_liability, false, false, false, false, false),
    OTHER(R.string.account_type_other, R.drawable.account_type_other, false, false, false, false, false);
}
