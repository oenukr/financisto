package ru.orangesoftware.financisto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.db.dao.*
import ru.orangesoftware.financisto.db.entity.*
// Import R for string resources if needed for error messages
// import ru.orangesoftware.financisto.R

class TransactionViewModel(
    application: Application,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val payeeDao: PayeeDao,
    private val currencyDao: CurrencyDao,
    private val transactionAttributeValueDao: TransactionAttributeValueDao
    // Potentially ProjectDao, LocationDao if those are directly settable on transaction
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TransactionFormState())
    val uiState: StateFlow<TransactionFormState> = _uiState.asStateFlow()

    // Temp var for generating unique IDs for new splits before they are saved to DB
    private var tempSplitIdCounter = -1L


    init {
        // Load initial data for selectors
        loadInitialChoiceData()
    }

    private fun loadInitialChoiceData() {
        viewModelScope.launch {
            // Concurrently load accounts, categories, payees, currencies
            // For simplicity, shown sequentially. In practice, use async/await or multiple launch blocks.

            accountDao.getAllActive().firstOrNull()?.let { accounts ->
                _uiState.update { currentFormState ->
                    var newMainTransaction = currentFormState.mainTransaction
                    var newSelectedAccountId = currentFormState.selectedAccountId
                    var newSelectedAccountTitle = currentFormState.selectedAccountTitle
                    var newSelectedAccountCurrencyId = currentFormState.selectedAccountCurrencyId

                    val preselectedAccountId = currentFormState.accountIdForNewTransaction
                    if (preselectedAccountId != null && currentFormState.selectedAccountId == null) {
                        val acc = accounts.find { a -> a.id == preselectedAccountId }
                        if (acc != null) {
                            newSelectedAccountId = acc.id
                            newSelectedAccountTitle = acc.title
                            newSelectedAccountCurrencyId = acc.currencyId
                            newMainTransaction = newMainTransaction.copy(fromAccountId = acc.id)
                        }
                    }
                    // If still no account selected (e.g. new tx without preselection, or preselected not found)
                    // and it's a new transaction, default to first account if available
                    if (newSelectedAccountId == null && currentFormState.isNewTransaction && accounts.isNotEmpty()) {
                        val firstAccount = accounts.first()
                        newSelectedAccountId = firstAccount.id
                        newSelectedAccountTitle = firstAccount.title
                        newSelectedAccountCurrencyId = firstAccount.currencyId
                        newMainTransaction = newMainTransaction.copy(fromAccountId = firstAccount.id)
                    }
                    currentFormState.copy(
                        availableAccounts = accounts,
                        selectedAccountId = newSelectedAccountId,
                        selectedAccountTitle = newSelectedAccountTitle,
                        selectedAccountCurrencyId = newSelectedAccountCurrencyId,
                        mainTransaction = newMainTransaction
                    )
                }
            }

            categoryDao.getAllSortedByLeft().firstOrNull()?.let { categories ->
                _uiState.update { it.copy(availableCategoriesTree = categories) }
                 // TODO: Set default category if needed (e.g., last used for account, or common one)
            }

            payeeDao.getAllActive().firstOrNull()?.let { payees ->
                _uiState.update { it.copy(availablePayees = payees) }
            }

            currencyDao.getAll().firstOrNull()?.let { currencies ->
                _uiState.update { it.copy(availableCurrencies = currencies) }
            }
        }
    }

    fun loadTransaction(
        transactionId: Long?,
        accountIdForNew: Long?,
        currentBalanceForUpdateMode: Long?,
        amountForNew: Long? // from intent extra AMOUNT_EXTRA for TransactionActivity
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    transactionIdForLoad = transactionId,
                    accountIdForNewTransaction = accountIdForNew,
                    amountForNewTransaction = amountForNew
                )
            }

            if (transactionId != null && transactionId != 0L) { // Editing existing transaction
                val transaction = transactionDao.getById(transactionId)
                if (transaction != null) {
                    val splits = transactionDao.getSplitTransactions(transactionId).firstOrNull() ?: emptyList() // Assuming getSplitTransactions returns Flow
                    val account = accountDao.getById(transaction.fromAccountId)
                    val category = categoryDao.getById(transaction.categoryId) // Need full path for display
                    val payee = payeeDao.getById(transaction.payeeId)
                    val originalCurrency = if (transaction.originalCurrencyId != 0L && transaction.originalCurrencyId != account?.currencyId) currencyDao.getById(transaction.originalCurrencyId) else null

                    // TODO: Fetch category path for selectedCategoryPath
                    // Example: selectedCategoryPath = buildCategoryPath(category, _uiState.value.availableCategoriesTree)
                    // TODO: Fetch attributes for main transaction and splits

                    _uiState.update {
                        it.copy(
                            mainTransaction = transaction,
                            splits = splits,
                            selectedAccountId = account?.id,
                            selectedAccountTitle = account?.title,
                            selectedAccountCurrencyId = account?.currencyId,
                            selectedCategoryId = category?.id,
                            selectedCategoryPath = category?.title, // Placeholder for path
                            isSplitCategorySelected = category?.id == -2L, // Assuming -2 is split cat ID (needs constant)
                            selectedPayeeId = payee?.id,
                            selectedPayeeName = payee?.title,
                            selectedOriginalCurrencyId = originalCurrency?.id,
                            selectedOriginalCurrencySymbol = originalCurrency?.symbol,
                            rateViewFromAmount = if (transaction.originalCurrencyId != 0L && transaction.originalCurrencyId != account?.currencyId) transaction.originalFromAmount else transaction.fromAmount,
                            rateViewToAmount = if (transaction.originalCurrencyId != 0L && transaction.originalCurrencyId != account?.currencyId) transaction.fromAmount else 0L,
                            isNewTransaction = false,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessages = listOf("Transaction not found")) } // TODO: Use R.string
                }
            } else { // New transaction
                var initialTransaction = TransactionEntity(
                    fromAccountId = _uiState.value.selectedAccountId ?: accountIdForNew ?: 0L, // Prioritize already selected/defaulted account
                    dateTime = System.currentTimeMillis(),
                    updatedOn = System.currentTimeMillis()
                )
                // If amountForNew is from an intent (e.g. widget shortcut)
                var initialRateFromAmount = amountForNew ?: 0L
                if (currentBalanceForUpdateMode != null) { // Update balance mode
                    initialRateFromAmount = 0L // Amount will be calculated
                }

                // Ensure account details are set if fromAccountId was just determined
                val currentSelectedAccountId = initialTransaction.fromAccountId
                val accountData = _uiState.value.availableAccounts.find { it.id == currentSelectedAccountId }

                _uiState.update {
                    it.copy(
                        mainTransaction = initialTransaction,
                        splits = emptyList(),
                        selectedAccountId = accountData?.id ?: currentSelectedAccountId,
                        selectedAccountTitle = accountData?.title ?: it.selectedAccountTitle,
                        selectedAccountCurrencyId = accountData?.currencyId ?: it.selectedAccountCurrencyId,
                        isNewTransaction = true,
                        isLoading = false,
                        isUpdateBalanceMode = currentBalanceForUpdateMode != null,
                        currentBalanceForUpdateMode = currentBalanceForUpdateMode ?: 0L,
                        rateViewFromAmount = initialRateFromAmount,
                        // Reset other fields to default for new transaction
                        selectedCategoryId = null, selectedCategoryPath = null, isSplitCategorySelected = false,
                        selectedPayeeId = null, selectedPayeeName = null,
                        selectedOriginalCurrencyId = null, selectedOriginalCurrencySymbol = null,
                        rateViewToAmount = 0L
                    )
                }
            }
             // If availableAccounts is still empty (e.g. race condition or initial load failed), try loading again.
            if (_uiState.value.availableAccounts.isEmpty()) {
                loadInitialChoiceData()
            }
        }
    }

    fun updateMainTransaction(updater: (TransactionEntity) -> TransactionEntity) {
        _uiState.update {
            val updatedTx = updater(it.mainTransaction)
            // Potentially re-calculate things if amount or currency changes
            // For now, just update the transaction. More logic later.
            it.copy(mainTransaction = updatedTx)
        }
    }

    // TODO: Add methods for:
    // - selectAccount(accountId: Long)
    // - selectCategory(categoryId: Long, categoryPath: String, isSplit: Boolean)
    // - selectPayee(payeeId: Long)
    // - selectOriginalCurrency(currencyId: Long?)
    // - updateRateViewAmounts(from: Long, to: Long) // This should also update mainTransaction.fromAmount/toAmount if originalCurrency is used
    // - addSplit(isTransfer: Boolean)
    // - updateSplit(split: TransactionEntity)
    // - deleteSplit(tempSplitIdOrDbId: Long)
    // - adjustSplits(...)
    // - saveTransaction()
    // - consumeSaveEvent(), consumeErrorMessages()
}
