package ru.orangesoftware.financisto.shared.model

// import ru.orangesoftware.financisto.R
// import ru.orangesoftware.financisto.utils.EntityEnum // Now in shared.model

enum class AccountType(
    override val titleIdPlaceholder: String, // From LocalizableEnum
    override val iconIdPlaceholder: String, // From EntityEnum
    val hasIssuer: Boolean,
    val hasNumber: Boolean,
    val isCard: Boolean,
    val isCreditCard: Boolean,
    val isElectronic: Boolean,
) : EntityEnum {

    CASH("account_type_cash", "account_type_cash", false, false, false, false, false),
    BANK("account_type_bank", "account_type_bank", true, false, false, false, false),
    DEBIT_CARD("account_type_debit_card", "account_type_card", true, true, true, false, false),
    CREDIT_CARD("account_type_credit_card", "account_type_card", true, true, true, true, false),
    ELECTRONIC("account_type_electronic", "account_type_electronic", false, false, false, false, true),
    ASSET("account_type_asset", "account_type_asset", false, false, false, false, false),
    LIABILITY("account_type_liability", "account_type_liability", false, false, false, false, false),
    OTHER("account_type_other", "account_type_other", false, false, false, false, false);
}
