// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.orangesoftware.financisto.transaction.mvi.TransactionEffect
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

internal class TransactionRateCoordinator(
    private val scope: CoroutineScope,
    private val ports: TransactionPorts,
    private val ioDispatcher: CoroutineDispatcher?,
    private val currentReady: () -> TransactionUiState.Ready?,
    private val publishReady: (TransactionUiState.Ready) -> Unit,
    private val sendEffect: (TransactionEffect) -> Unit,
) {
    private var rateJob: Job? = null

    fun cancel(clearFetchingFlag: Boolean = false) {
        rateJob?.cancel()
        rateJob = null
        if (clearFetchingFlag) {
            val ready = currentReady()
            if (ready != null && ready.isFetchingRate) {
                publishReady(ready.copy(isFetchingRate = false))
            }
        }
    }

    fun fetchLiveRate(ready: TransactionUiState.Ready) {
        val pCur = ready.primaryCurrency ?: return
        val sCur = ready.secondaryCurrency?.takeIf { it.id != pCur.id } ?: return
        cancel()
        val fromId = pCur.id
        val toId = sCur.id
        publishReady(ready.copy(isFetchingRate = true))
        rateJob = scope.launch {
            val outcome = TransactionActionRunner.runOn(ioDispatcher) {
                ports.exchangeRatePort.fetchLiveRate(fromId, toId)
            }
            applyLiveRateOutcome(fromId, toId, outcome)
        }
    }

    fun maybeLookupStoredRate(
        ready: TransactionUiState.Ready,
        syncInitialSnapshotIfUntouched: Boolean = false,
    ) {
        cancel()
        val base = if (ready.isFetchingRate) {
            ready.copy(isFetchingRate = false).also(publishReady)
        } else {
            ready
        }
        val pCur = base.primaryCurrency ?: return
        val sCur = base.secondaryCurrency?.takeIf { it.id != pCur.id }
        if (sCur != null && base.form.exchangeRate <= 0.0) {
            val fromId = pCur.id
            val toId = sCur.id
            rateJob = scope.launch {
                val stored = TransactionActionRunner.runOn(ioDispatcher) {
                    ports.repository.getLatestExchangeRate(fromId, toId)
                } ?: return@launch
                applyStoredRate(fromId, toId, stored, syncInitialSnapshotIfUntouched)
            }
        }
    }

    private fun applyLiveRateOutcome(fromId: Long, toId: Long, outcome: Result<Double>) {
        val latest = currentReady()?.takeUnless { it.isSaving } ?: return
        val latestPrimary = latest.primaryCurrency?.takeIf { it.id == fromId }
        val latestSecondary = latest.secondaryCurrency?.takeIf { it.id == toId }
        if (latestPrimary == null || latestSecondary == null) {
            if (latest.isFetchingRate) {
                publishReady(latest.copy(isFetchingRate = false))
            }
        } else {
            outcome.onSuccess { rate ->
                val formatted = CurrencyConversionCalculator.formatRate(rate)
                val updated = AmountModeReducer.applyExchangeRateText(
                    latest.copy(isFetchingRate = false),
                    formatted,
                    latestPrimary.decimals,
                    latestSecondary.decimals,
                )
                publishReady(updated)
            }.onFailure { error ->
                publishReady(latest.copy(isFetchingRate = false))
                sendEffect(TransactionEffect.ShowMessage(error.localizedMessage ?: "Rate download failed"))
            }
        }
    }

    private fun applyStoredRate(
        fromId: Long,
        toId: Long,
        storedRate: Double,
        syncInitialSnapshotIfUntouched: Boolean,
    ) {
        val latest = currentReady()?.takeUnless { it.isSaving || it.form.exchangeRate > 0.0 } ?: return
        val latestPrimary = latest.primaryCurrency?.takeIf { it.id == fromId } ?: return
        val latestSecondary = latest.secondaryCurrency?.takeIf { it.id == toId }
        if (latestSecondary != null) {
            val wasUntouched = latest.form == latest.initialFormSnapshot
            val formatted = CurrencyConversionCalculator.formatRate(storedRate)
            val updated = AmountModeReducer.applyExchangeRateText(
                latest,
                formatted,
                latestPrimary.decimals,
                latestSecondary.decimals,
            )
            val finalState = if (syncInitialSnapshotIfUntouched && wasUntouched) {
                updated.copy(initialFormSnapshot = updated.form)
            } else {
                updated
            }
            publishReady(finalState)
        }
    }
}
