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
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.utils.FuturePlanner
import java.util.Date

sealed interface PlannerUiState {
    data object Loading : PlannerUiState

    data class Success(
        val transactions: List<TransactionInfo>,
        val totals: Array<Total>,
        val filter: WhereFilter,
    ) : PlannerUiState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            if (transactions != other.transactions) return false
            if (!totals.contentEquals(other.totals)) return false
            return filter == other.filter
        }

        override fun hashCode(): Int {
            var result = transactions.hashCode()
            result = 31 * result + totals.contentHashCode()
            result = 31 * result + filter.hashCode()
            return result
        }
    }

    data class Empty(
        val totals: Array<Total> = emptyArray(),
        val filter: WhereFilter,
    ) : PlannerUiState {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Empty) return false
            if (!totals.contentEquals(other.totals)) return false
            return filter == other.filter
        }

        override fun hashCode(): Int {
            var result = filter.hashCode()
            result = 31 * result + totals.contentHashCode()
            return result
        }
    }

    data class Error(
        val message: String?,
        val cause: Throwable? = null,
    ) : PlannerUiState
}

@Suppress("InjectDispatcher")
class PlannerViewModel(
    private val db: DatabaseAdapter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlannerUiState>(PlannerUiState.Loading)
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    @Suppress("TooGenericExceptionCaught")
    fun loadPlannedTransactions(filter: WhereFilter, now: Date = Date()) {
        loadJob?.cancel()
        val filterCopy = WhereFilter.copyOf(filter)
        loadJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = PlannerUiState.Loading
            try {
                val planner = FuturePlanner(db, filterCopy, now)
                val data = planner.getPlannedTransactionsWithTotals()
                coroutineContext.ensureActive()
                if (data.transactions.isEmpty()) {
                    _uiState.value = PlannerUiState.Empty(
                        totals = data.totals,
                        filter = filterCopy,
                    )
                } else {
                    _uiState.value = PlannerUiState.Success(
                        transactions = data.transactions,
                        totals = data.totals,
                        filter = filterCopy,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                _uiState.value = PlannerUiState.Error(
                    message = e.localizedMessage ?: "Failed to calculate planned transactions",
                    cause = e,
                )
            }
        }
    }
}
