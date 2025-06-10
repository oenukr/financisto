package ru.orangesoftware.financisto.viewmodel

import ru.orangesoftware.financisto.db.entity.*
// Using existing model classes for selected items temporarily for display purposes,
// might be replaced by specific DisplayData classes later.
// For now, we'll primarily use IDs and simple string representations if needed for display.

data class TransactionFormState(
    val mainTransaction: TransactionEntity = TransactionEntity(dateTime = System.currentTimeMillis(), updatedOn = System.currentTimeMillis()),
    val splits: List<TransactionEntity> = emptyList(),

    val selectedAccountId: Long? = null,
    val selectedAccountTitle: String? = null, // For display
    val selectedAccountCurrencyId: Long? = null, // For RateView

    val selectedCategoryId: Long? = null,
    val selectedCategoryPath: String? = null, // For display (e.g., "Expenses : Food")
    val isSplitCategorySelected: Boolean = false,

    val selectedPayeeId: Long? = null,
    val selectedPayeeName: String? = null, // For display

    val selectedOriginalCurrencyId: Long? = null, // null means same as account currency
    val selectedOriginalCurrencySymbol: String? = null, // For display in RateView

    val availableAccounts: List<AccountEntity> = emptyList(),
    val availableCategoriesTree: List<CategoryEntity> = emptyList(),
    val categoryDisplayPathMap: Map<Long, String> = emptyMap(), // Added for paths
    val availablePayees: List<PayeeEntity> = emptyList(),
    val availableCurrencies: List<CurrencyEntity> = emptyList(),

    val unsplitAmount: Long = 0L,
    val rateViewFromAmount: Long = 0L, // Amount in 'from' currency (original or account)
    val rateViewToAmount: Long = 0L,   // Amount in 'to' currency (account currency if original is different)

    val isUpdateBalanceMode: Boolean = false,
    val currentBalanceForUpdateMode: Long = 0L,
    val differenceForUpdateMode: Long = 0L,

    val transactionIdForLoad: Long? = null, // ID of transaction being edited, or null for new
    val accountIdForNewTransaction: Long? = null, // Pre-selected account for new transaction
    val amountForNewTransaction: Long? = null, // Pre-filled amount for new transaction (e.g. from widget)


    val isLoading: Boolean = false,
    val errorMessages: List<String> = emptyList(), // For multiple validation errors
    val saveEvent: Event<Boolean>? = null, // True for success
    val isNewTransaction: Boolean = true // Derived when loading
)
