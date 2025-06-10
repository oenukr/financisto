package ru.orangesoftware.financisto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.db.dao.*
import ru.orangesoftware.financisto.db.entity.*
import ru.orangesoftware.financisto.R // For string resources

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

    private var tempSplitIdCounter = -1L


    init {
        loadInitialChoiceData()
    }

    private fun loadInitialChoiceData() {
        viewModelScope.launch {
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
                val categoryPathMap = buildCategoryPathMap(categories)
                _uiState.update {
                    it.copy(
                        availableCategoriesTree = categories,
                        categoryDisplayPathMap = categoryPathMap
                    )
                }
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
        amountForNew: Long?
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

            if (transactionId != null && transactionId != 0L) {
                val transaction = transactionDao.getById(transactionId)
                if (transaction != null) {
                    val splits = transactionDao.getSplitTransactions(transactionId).firstOrNull() ?: emptyList()
                    val account = accountDao.getById(transaction.fromAccountId)
                    val category = categoryDao.getById(transaction.categoryId)
                    val payee = payeeDao.getById(transaction.payeeId)
                    val originalCurrency = if (transaction.originalCurrencyId != 0L && transaction.originalCurrencyId != account?.currencyId) currencyDao.getById(transaction.originalCurrencyId) else null

                    val categoryPath = if (category != null) _uiState.value.categoryDisplayPathMap[category.id] ?: category.title else null

                    _uiState.update {
                        it.copy(
                            mainTransaction = transaction,
                            splits = splits,
                            selectedAccountId = account?.id,
                            selectedAccountTitle = account?.title,
                            selectedAccountCurrencyId = account?.currencyId,
                            selectedCategoryId = category?.id,
                            selectedCategoryPath = categoryPath,
                            isSplitCategorySelected = category?.id == CategorySelector.SPLIT_CATEGORY_ID_CONSTANT,
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
                    _uiState.update { it.copy(isLoading = false, errorMessages = listOf(getApplication<Application>().getString(R.string.transaction_not_found))) }
                }
            } else {
                var initialTransaction = TransactionEntity(
                    fromAccountId = _uiState.value.selectedAccountId ?: accountIdForNew ?: 0L,
                    dateTime = System.currentTimeMillis(),
                    updatedOn = System.currentTimeMillis()
                )
                var initialRateFromAmount = amountForNew ?: 0L
                if (currentBalanceForUpdateMode != null) {
                    initialRateFromAmount = 0L
                }

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
                        selectedCategoryId = null, selectedCategoryPath = null, isSplitCategorySelected = false,
                        selectedPayeeId = null, selectedPayeeName = null,
                        selectedOriginalCurrencyId = null, selectedOriginalCurrencySymbol = null,
                        rateViewToAmount = 0L
                    )
                }
            }
            if (_uiState.value.availableAccounts.isEmpty() || _uiState.value.availableCategoriesTree.isEmpty()) {
                loadInitialChoiceData() // Ensure all choice data is loaded
            }
        }
    }

    fun updateMainTransaction(updater: (TransactionEntity) -> TransactionEntity) {
        _uiState.update {
            val updatedTx = updater(it.mainTransaction)
            if (it.mainTransaction.fromAmount != updatedTx.fromAmount && !it.isSplitCategorySelected) {
                if (it.selectedOriginalCurrencyId == null || it.selectedOriginalCurrencyId == 0L) {
                    it.copy(mainTransaction = updatedTx, rateViewFromAmount = updatedTx.fromAmount)
                } else {
                    it.copy(mainTransaction = updatedTx, rateViewToAmount = updatedTx.fromAmount)
                }
            } else {
                it.copy(mainTransaction = updatedTx)
            }
        }
        if (_uiState.value.isUpdateBalanceMode) {
             updateDifferenceForUpdateMode()
        }
        if (!_uiState.value.isSplitCategorySelected) {
            recalculateUnsplitAmount()
        }
    }

    fun selectAccount(accountId: Long) {
        viewModelScope.launch {
            val account = accountDao.getById(accountId)
            if (account != null) {
                _uiState.update {
                    val newOriginalCurrencyId = if (it.selectedOriginalCurrencyId == null || it.selectedOriginalCurrencyId == it.selectedAccountCurrencyId) {
                        null
                    } else {
                        it.selectedOriginalCurrencyId
                    }
                    val newOriginalCurrencySymbol = if (newOriginalCurrencyId == null) {
                        null
                    } else {
                        currencyDao.getById(newOriginalCurrencyId)?.symbol
                    }

                    it.copy(
                        selectedAccountId = account.id,
                        selectedAccountTitle = account.title,
                        selectedAccountCurrencyId = account.currencyId,
                        mainTransaction = it.mainTransaction.copy(fromAccountId = account.id),
                        selectedOriginalCurrencyId = newOriginalCurrencyId,
                        selectedOriginalCurrencySymbol = newOriginalCurrencySymbol
                    )
                }
            }
        }
    }

    fun selectCategory(categoryId: Long, categoryPath: String) {
        val isSplit = categoryId == CategorySelector.SPLIT_CATEGORY_ID_CONSTANT
        _uiState.update {
            it.copy(
                selectedCategoryId = categoryId,
                selectedCategoryPath = categoryPath,
                isSplitCategorySelected = isSplit,
                mainTransaction = it.mainTransaction.copy(categoryId = categoryId)
            )
        }
        if (isSplit && _uiState.value.splits.isEmpty()) {
             addSplit(false)
        }
        recalculateUnsplitAmount()
    }

    fun selectPayee(payeeId: Long) {
        viewModelScope.launch {
            val payee = payeeDao.getById(payeeId)
            _uiState.update {
                it.copy(
                    selectedPayeeId = payee?.id,
                    selectedPayeeName = payee?.title,
                    mainTransaction = it.mainTransaction.copy(payeeId = payeeId)
                )
            }
        }
    }

    fun selectOriginalCurrency(currencyId: Long?) {
        viewModelScope.launch {
            val currency = if (currencyId != null && currencyId != 0L) currencyDao.getById(currencyId) else null
            val accountCurrencyId = _uiState.value.selectedAccountCurrencyId

            val effectiveOriginalCurrencyId = if (currency?.id == accountCurrencyId) null else currency?.id
            val effectiveOriginalCurrencySymbol = if (currency?.id == accountCurrencyId) null else currency?.symbol

            _uiState.update {
                it.copy(
                    selectedOriginalCurrencyId = effectiveOriginalCurrencyId,
                    selectedOriginalCurrencySymbol = effectiveOriginalCurrencySymbol,
                    mainTransaction = it.mainTransaction.copy(
                        originalCurrencyId = effectiveOriginalCurrencyId ?: 0L
                    )
                )
            }
        }
    }

    fun updateRateViewAmounts(fromAmount: Long, toAmount: Long? = null) {
        _uiState.update {
            val newToAmount = toAmount ?: if (it.selectedOriginalCurrencyId == null || it.selectedOriginalCurrencyId == 0L || it.selectedOriginalCurrencyId == it.selectedAccountCurrencyId) {
                fromAmount
            } else {
                it.rateViewToAmount
            }
            it.copy(
                rateViewFromAmount = fromAmount,
                rateViewToAmount = newToAmount,
                mainTransaction = it.mainTransaction.copy(
                    originalFromAmount = if (it.selectedOriginalCurrencyId != null && it.selectedOriginalCurrencyId != 0L && it.selectedOriginalCurrencyId != it.selectedAccountCurrencyId) fromAmount else 0L,
                    fromAmount = if (it.selectedOriginalCurrencyId != null && it.selectedOriginalCurrencyId != 0L && it.selectedOriginalCurrencyId != it.selectedAccountCurrencyId) newToAmount else fromAmount
                )
            )
        }
        if (_uiState.value.isUpdateBalanceMode) {
            updateDifferenceForUpdateMode()
        }
        recalculateUnsplitAmount()
    }

    private fun updateDifferenceForUpdateMode() {
        _uiState.update {
            val difference = it.rateViewFromAmount - it.currentBalanceForUpdateMode
            it.copy(differenceForUpdateMode = difference)
        }
    }

    private fun recalculateUnsplitAmount() {
        _uiState.update {
            val totalSplitAmount = it.splits.sumOf { s -> s.fromAmount }
            val mainAmount = it.rateViewFromAmount
            it.copy(unsplitAmount = mainAmount - totalSplitAmount)
        }
    }

    fun addSplit(isTransfer: Boolean) {
        tempSplitIdCounter--
        val newSplit = TransactionEntity(
            id = tempSplitIdCounter,
            parentId = _uiState.value.mainTransaction.id.takeIf { it != 0L } ?: 0L,
            fromAccountId = _uiState.value.selectedAccountId ?: 0L,
            dateTime = _uiState.value.mainTransaction.dateTime,
            fromAmount = _uiState.value.unsplitAmount.takeIf { it != 0L && _uiState.value.splits.isNotEmpty() } ?: _uiState.value.rateViewFromAmount.takeIf { _uiState.value.splits.isEmpty() } ?: 0L
        )
        _uiState.update {
            val updatedSplits = it.splits + newSplit
            it.copy(splits = updatedSplits)
        }
        recalculateUnsplitAmount()
    }

    fun updateSplit(splitToUpdate: TransactionEntity) {
        _uiState.update {
            val updatedSplits = it.splits.map { s ->
                if (s.id == splitToUpdate.id) splitToUpdate else s
            }
            it.copy(splits = updatedSplits)
        }
        recalculateUnsplitAmount()
    }

    fun deleteSplit(splitId: Long) {
        _uiState.update {
            val updatedSplits = it.splits.filterNot { s -> s.id == splitId }
            it.copy(splits = updatedSplits)
        }
        recalculateUnsplitAmount()
    }

    fun saveTransaction() {
        viewModelScope.launch {
            var currentTx = _uiState.value.mainTransaction
            val currentState = _uiState.value

            if (currentState.selectedOriginalCurrencyId != null && currentState.selectedOriginalCurrencyId != 0L && currentState.selectedOriginalCurrencyId != currentState.selectedAccountCurrencyId) {
                currentTx = currentTx.copy(
                    originalCurrencyId = currentState.selectedOriginalCurrencyId,
                    originalFromAmount = currentState.rateViewFromAmount,
                    fromAmount = currentState.rateViewToAmount
                )
            } else {
                currentTx = currentTx.copy(
                    originalCurrencyId = 0L,
                    originalFromAmount = 0L,
                    fromAmount = currentState.rateViewFromAmount
                )
            }

            if (currentState.isUpdateBalanceMode) {
                currentTx = currentTx.copy(
                    fromAmount = currentState.differenceForUpdateMode,
                    originalFromAmount = 0L,
                    originalCurrencyId = 0L,
                    note = "${getApplication<Application>().getString(R.string.update_balance_transaction_note)} ${currentTx.note ?: ""}".trim()
                )
            }
            currentTx = currentTx.copy(updatedOn = System.currentTimeMillis())

            if (currentTx.fromAccountId == 0L) {
                _uiState.update { it.copy(errorMessages = listOf(getApplication<Application>().getString(R.string.select_account))) }; return@launch
            }
            if (currentTx.categoryId == 0L && !currentState.isSplitCategorySelected) {
                 _uiState.update { it.copy(errorMessages = listOf(getApplication<Application>().getString(R.string.select_category))) }; return@launch
            }
            if (currentState.isSplitCategorySelected && currentState.unsplitAmount != 0L) {
                _uiState.update { it.copy(errorMessages = listOf(getApplication<Application>().getString(R.string.unsplit_amount_not_zero))) }; return@launch
            }
            if (currentState.isSplitCategorySelected && currentState.splits.isEmpty()){
                 _uiState.update { it.copy(errorMessages = listOf(getApplication<Application>().getString(R.string.split_no_splits))) }; return@launch
            }

            try {
                val savedMainTxId: Long
                if (currentState.isNewTransaction || currentTx.id == 0L) {
                    savedMainTxId = transactionDao.insert(currentTx.copy(id = 0L))
                } else {
                    transactionDao.update(currentTx)
                    savedMainTxId = currentTx.id
                }

                if (currentState.isSplitCategorySelected) {
                    transactionDao.deleteSplitsByParentId(savedMainTxId)
                    currentState.splits.forEach { split ->
                        val splitToSave = split.copy(
                            parentId = savedMainTxId,
                            fromAccountId = currentTx.fromAccountId,
                            dateTime = currentTx.dateTime,
                            id = if (split.id < 0) 0L else split.id
                        )
                        transactionDao.insert(splitToSave)
                    }
                } else {
                    transactionDao.deleteSplitsByParentId(savedMainTxId)
                }

                _uiState.update { it.copy(errorMessages = emptyList(), saveEvent = Event(true)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessages = listOf(e.message ?: getApplication<Application>().getString(R.string.error_saving_transaction))) }
            }
        }
    }

    fun consumeSaveEvent() {
        _uiState.update { it.copy(saveEvent = null) }
    }

    fun consumeErrorMessages() {
        _uiState.update { it.copy(errorMessages = emptyList()) }
    }

    private fun buildCategoryPathMap(categoryTree: List<CategoryEntity>): Map<Long, String> {
        val pathMap = mutableMapOf<Long, String>()
        val parentStack = mutableListOf<CategoryEntity>()

        for (categoryNode in categoryTree) {
            while (parentStack.isNotEmpty() && parentStack.last().right < categoryNode.right) {
                parentStack.removeAt(parentStack.size - 1)
            }

            val currentPath = parentStack.joinToString(" : ") { it.title }
            val displayTitle = if (currentPath.isNotEmpty()) {
                "$currentPath : ${categoryNode.title}"
            } else {
                categoryNode.title
            }
            pathMap[categoryNode.id] = displayTitle

            if (categoryNode.right > categoryNode.left + 1) {
                parentStack.add(categoryNode)
            }
        }
        return pathMap
    }
}
