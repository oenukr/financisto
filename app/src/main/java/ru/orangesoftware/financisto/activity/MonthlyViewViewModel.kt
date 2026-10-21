// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.utils.MonthlyViewPlanner
import java.util.Date

sealed interface MonthlyViewUiState {
    data object Loading : MonthlyViewUiState

    data class Success(
        val transactions: List<TransactionInfo>,
        val total: Long,
        val isStatementPreview: Boolean,
    ) : MonthlyViewUiState

    data class Empty(
        val total: Long = 0L,
        val isStatementPreview: Boolean = false,
    ) : MonthlyViewUiState

    data class Error(
        val message: String?,
        val cause: Throwable? = null,
    ) : MonthlyViewUiState
}

@Suppress("InjectDispatcher")
class MonthlyViewViewModel(
    private val db: DatabaseAdapter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MonthlyViewUiState>(MonthlyViewUiState.Loading)
    val uiState: StateFlow<MonthlyViewUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    fun getAccount(accountId: Long): Account? = db.getAccount(accountId)

    fun getCustomClosingDay(accountId: Long, periodKey: Int): Int =
        db.getCustomClosingDay(accountId, periodKey)

    @Suppress("TooGenericExceptionCaught")
    fun loadMonthlyPreview(
        account: Account,
        isStatementPreview: Boolean,
        open: Date,
        close: Date,
        now: Date = Date(),
    ) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = MonthlyViewUiState.Loading
            try {
                val planner = MonthlyViewPlanner(db, account, isStatementPreview, open, close, now)
                val transactionList = if (isStatementPreview) {
                    planner.creditCardStatement
                } else {
                    planner.plannedTransactionsWithTotals
                }
                coroutineContext.ensureActive()
                val transactions = transactionList.transactions ?: emptyList()
                val total = transactionList.totals?.firstOrNull()?.balance ?: 0L
                if (transactions.isEmpty()) {
                    _uiState.value = MonthlyViewUiState.Empty(
                        total = total,
                        isStatementPreview = isStatementPreview,
                    )
                } else {
                    _uiState.value = MonthlyViewUiState.Success(
                        transactions = transactions,
                        total = total,
                        isStatementPreview = isStatementPreview,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                _uiState.value = MonthlyViewUiState.Error(
                    message = e.localizedMessage ?: "Failed to load monthly preview",
                    cause = e,
                )
            }
        }
    }
}
