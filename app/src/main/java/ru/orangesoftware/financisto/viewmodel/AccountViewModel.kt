package ru.orangesoftware.financisto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.db.dao.AccountDao
import ru.orangesoftware.financisto.db.dao.CurrencyDao
import ru.orangesoftware.financisto.db.dao.TransactionDao
import ru.orangesoftware.financisto.db.entity.AccountEntity
import ru.orangesoftware.financisto.db.entity.CurrencyEntity
import ru.orangesoftware.financisto.db.entity.TransactionEntity
import ru.orangesoftware.financisto.model.AccountType // For default type
import ru.orangesoftware.financisto.R // For string resources

// Data class to hold the form state
data class AccountFormState(
    val account: AccountEntity = AccountEntity( // Default new account
        type = AccountType.GENERAL.name, // Default type
        isActive = true,
        isIncludeIntoTotals = true,
        updatedOn = System.currentTimeMillis()
    ),
    val isNewAccount: Boolean = true,
    val openingBalance: Long = 0L, // Only for new accounts
    val isLoading: Boolean = false,
    val errorResId: Int? = null,
    val saveResult: Event<SaveResult>? = null // Event wrapper for navigation
)

data class SaveResult(val success: Boolean, val accountId: Long)

// Simple event wrapper
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
    fun peekContent(): T = content
}


class AccountViewModel(
    application: Application, // For string resources if needed, and MyPreferences
    private val accountDao: AccountDao,
    private val currencyDao: CurrencyDao,
    private val transactionDao: TransactionDao // For opening balance transaction
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AccountFormState())
    val uiState: StateFlow<AccountFormState> = _uiState.asStateFlow()

    private val _currencies = MutableStateFlow<List<CurrencyEntity>>(emptyList())
    val currencies: StateFlow<List<CurrencyEntity>> = _currencies.asStateFlow()

    init {
        loadCurrencies()
    }

    private fun loadCurrencies() {
        viewModelScope.launch {
            // Assuming currencyDao.getAll() returns Flow<List<CurrencyEntity>>
            currencyDao.getAll().collect { currencyList ->
                _currencies.value = currencyList
                // If new account and no currencyId set, pick first available or home currency
                if (_uiState.value.isNewAccount && _uiState.value.account.currencyId == 0L && currencyList.isNotEmpty()) {
                    val homeCurrency = currencyList.firstOrNull { it.isDefault } ?: currencyList.first()
                    _uiState.update { it.copy(account = it.account.copy(currencyId = homeCurrency.id)) }
                }
            }
        }
    }

    fun loadAccount(accountId: Long?) {
        if (accountId == null || accountId == -1L || accountId == 0L) {
            // New account mode, ensure default currency is set if currencies are loaded
            val currentCurrencyList = _currencies.value
            val currentAccount = _uiState.value.account.copy( // Create a fresh default account for "new" mode
                type = AccountType.GENERAL.name,
                isActive = true,
                isIncludeIntoTotals = true,
                updatedOn = System.currentTimeMillis(),
                // Reset other fields to default for a truly new account form
                id = 0L, title = "", issuer = null, number = null, totalAmount = 0L, sortOrder = 0,
                lastCategoryId = 0L, lastAccountId = 0L, closingDay = 0, paymentDay = 0,
                lastTransactionDate = 0L, limitAmount = 0L
            )
            if (currentAccount.currencyId == 0L && currentCurrencyList.isNotEmpty()){
                 val homeCurrency = currentCurrencyList.firstOrNull { it.isDefault } ?: currentCurrencyList.first()
                _uiState.value = AccountFormState(account = currentAccount.copy(currencyId = homeCurrency.id), isNewAccount = true)
            } else {
                 _uiState.value = AccountFormState(account = currentAccount, isNewAccount = true)
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val account = accountDao.getById(accountId)
            if (account != null) {
                _uiState.value = AccountFormState(account = account, isNewAccount = false, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, errorResId = R.string.account_not_found)
            }
        }
    }

    fun updateAccountField(updater: (AccountEntity) -> AccountEntity) {
        _uiState.update { it.copy(account = updater(it.account)) }
    }

    fun setOpeningBalance(balance: Long) {
        _uiState.update { it.copy(openingBalance = balance) }
    }

    fun saveAccount() {
        val currentState = _uiState.value
        // Ensure updatedOn is set at the point of saving
        val accountToSave = currentState.account.copy(updatedOn = System.currentTimeMillis())


        // Basic Validation (can be expanded)
        if (accountToSave.title.isNullOrBlank()) {
            // Using a placeholder string resource name, actual should be verified/created
            _uiState.value = currentState.copy(errorResId = R.string.error_title_empty, saveResult = null)
            return
        }
        if (accountToSave.currencyId == 0L) {
            _uiState.value = currentState.copy(errorResId = R.string.select_currency, saveResult = null)
            return
        }

        val accountType = try { AccountType.valueOf(accountToSave.type ?: "") } catch (e: Exception) { null }
        if (accountType == AccountType.CREDIT_CARD) {
            if (accountToSave.closingDay < 0 || accountToSave.closingDay > 31) { // Basic range check
                 _uiState.value = currentState.copy(errorResId = R.string.closing_day_error, saveResult = null)
                return
            }
            if (accountToSave.paymentDay < 0 || accountToSave.paymentDay > 31) { // Basic range check
                _uiState.value = currentState.copy(errorResId = R.string.payment_day_error, saveResult = null)
                return
            }
        }

        viewModelScope.launch {
            val savedAccountId: Long
            if (currentState.isNewAccount) {
                // Explicitly set id to 0 for insert, though Room usually handles it.
                savedAccountId = accountDao.insert(accountToSave.copy(id = 0L))
                if (currentState.openingBalance != 0L && savedAccountId > 0L) {
                    val openingTransaction = TransactionEntity(
                        fromAccountId = savedAccountId,
                        // toAccountId will default to 0 as per Entity definition
                        categoryId = 0, // TODO: Define/fetch a specific "Opening Balance" category ID
                        note = getApplication<Application>().getString(R.string.opening_amount) + " (" + accountToSave.title + ")",
                        fromAmount = currentState.openingBalance,
                        // toAmount will default to 0
                        dateTime = System.currentTimeMillis(),
                        originalCurrencyId = accountToSave.currencyId, // Opening balance in account's currency
                        originalFromAmount = currentState.openingBalance, // Store original amount
                        // Other fields like locationId, projectId, payeeId will default to 0
                        isTemplate = 0, // Not a template
                        status = "CL" // Default to Cleared
                        // updatedOn will default to 0
                    )
                    transactionDao.insert(openingTransaction)
                }
            } else {
                accountDao.update(accountToSave)
                savedAccountId = accountToSave.id
            }
            // Clear error on successful save attempt before setting result
            _uiState.value = currentState.copy(errorResId = null, saveResult = Event(SaveResult(true, savedAccountId)))
        }
    }
     fun consumeError() {
        _uiState.update { it.copy(errorResId = null) }
    }
}
