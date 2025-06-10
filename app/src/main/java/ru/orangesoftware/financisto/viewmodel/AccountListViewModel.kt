package ru.orangesoftware.financisto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.db.dao.AccountDao
import ru.orangesoftware.financisto.db.dao.CurrencyDao
import ru.orangesoftware.financisto.db.entity.AccountEntity
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.repository.AccountRepository
import ru.orangesoftware.financisto.utils.MyPreferences


data class AccountDisplayData(
    val accountEntity: AccountEntity,
    val currencySymbol: String
)

class AccountListViewModel(
    private val application: Application, // For MyPreferences
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val currencyDao: CurrencyDao // Injected CurrencyDao
) : ViewModel() {

    private val _showActiveOnly = MutableStateFlow(true)
    val showActiveOnly: StateFlow<Boolean> = _showActiveOnly

    private val currencySymbolMap: StateFlow<Map<Long, String>> =
        currencyDao.getAll() // Assuming this returns Flow<List<CurrencyEntity>>
            .map { list -> list.associateBy({ it.id }, { it.symbol ?: "" }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Modified to emit AccountDisplayData
    val accountsDisplayData: StateFlow<List<AccountDisplayData>> =
        _showActiveOnly.flatMapLatest { activeOnly ->
            // TODO: Incorporate sortOrder from MyPreferences if AccountDao.getAccounts supports it
            // val sortProperty = MyPreferences.getAccountSortOrder(application.applicationContext).property
            // val sortAsc = MyPreferences.getAccountSortOrder(application.applicationContext).asc
            accountDao.getAccounts(
                isActiveOnly = activeOnly,
                includeAccountIds = emptyList()
                // sortOrderProperty = sortProperty, // Add if DAO supports
                // isSortAsc = sortAsc             // Add if DAO supports
            )
        }.combine(currencySymbolMap) { accountList, symbolsMap ->
            accountList.map { accEntity ->
                AccountDisplayData(
                    accEntity,
                    symbolsMap[accEntity.currencyId] ?: "$" // Fallback symbol
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _totalBalance = MutableStateFlow<Total?>(null)
    val totalBalance: StateFlow<Total?> = _totalBalance

    init {
        refreshTotals() // Initial refresh
    }

    fun refreshTotals() {
        viewModelScope.launch {
            try {
                _totalBalance.value = accountRepository.getAccountsTotalInHomeCurrency()
            } catch (e: Exception) {
                 _totalBalance.value = Total(null, Total.TotalError.UNKNOWN_ERROR)
            }
        }
    }

    fun setShowActiveOnly(isActive: Boolean) {
        _showActiveOnly.value = isActive
        // If totals should reflect activeOnly filter, uncomment:
        // refreshTotals()
    }

    // Updated to return AccountDisplayData
    suspend fun getAccountDisplayDataById(id: Long): AccountDisplayData? {
        val account = accountDao.getById(id)
        return account?.let {
            val symbol = currencySymbolMap.value[it.currencyId] ?: "$" // Fallback symbol
            AccountDisplayData(it, symbol)
        }
    }

    // getAccountById might still be needed internally or by other components
    suspend fun getAccountById(id: Long): AccountEntity? {
        return accountDao.getById(id)
    }

    suspend fun flipAccountActiveState(accountId: Long): Boolean {
        val account = accountDao.getById(accountId)
        if (account != null) {
            val updatedAccount = AccountEntity(
                id = account.id,
                title = account.title,
                creationDate = account.creationDate,
                currencyId = account.currencyId,
                type = account.type,
                issuer = account.issuer,
                number = account.number,
                totalAmount = account.totalAmount,
                sortOrder = account.sortOrder,
                lastCategoryId = account.lastCategoryId,
                lastAccountId = account.lastAccountId,
                closingDay = account.closingDay,
                paymentDay = account.paymentDay,
                isIncludeIntoTotals = account.isIncludeIntoTotals,
                isActive = !account.isActive, // Flipped
                limitAmount = account.limitAmount,
                lastTransactionDate = account.lastTransactionDate,
                updatedOn = System.currentTimeMillis()
            )
            accountDao.update(updatedAccount)
            return true
        }
        return false
    }

    fun saveAccount(accountToSave: AccountEntity) { // Renamed parameter
        viewModelScope.launch {
            val accountWithTimestamp = AccountEntity(
                id = accountToSave.id,
                title = accountToSave.title,
                creationDate = accountToSave.creationDate,
                currencyId = accountToSave.currencyId,
                type = accountToSave.type,
                issuer = accountToSave.issuer,
                number = accountToSave.number,
                totalAmount = accountToSave.totalAmount,
                sortOrder = accountToSave.sortOrder,
                lastCategoryId = accountToSave.lastCategoryId,
                lastAccountId = accountToSave.lastAccountId,
                closingDay = accountToSave.closingDay,
                paymentDay = accountToSave.paymentDay,
                isIncludeIntoTotals = accountToSave.isIncludeIntoTotals,
                isActive = accountToSave.isActive,
                limitAmount = accountToSave.limitAmount,
                lastTransactionDate = accountToSave.lastTransactionDate,
                updatedOn = System.currentTimeMillis() // Set/update timestamp
            )
            if (accountWithTimestamp.id == 0L) {
                accountDao.insert(accountWithTimestamp)
            } else {
                accountDao.update(accountWithTimestamp)
            }
            // refreshTotals() // Consider if totals should be refreshed after any save
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            accountDao.deleteById(id)
        }
    }
}
