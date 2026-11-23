// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import ru.orangesoftware.financisto.transaction.mvi.ExternalEntityKind
import ru.orangesoftware.financisto.transaction.mvi.TransactionEffect
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import timber.log.Timber

internal class TransactionExternalEntityCoordinator(
    private val scope: CoroutineScope,
    private val ports: TransactionPorts,
    private val savedStateHandle: SavedStateHandle,
    private val currentReady: () -> TransactionUiState.Ready?,
    private val publishReady: (TransactionUiState.Ready) -> Unit,
    private val sendEffect: (TransactionEffect) -> Unit,
    private val ioDispatcher: CoroutineDispatcher? = null,
    private val currentGeneration: () -> Long = { 0L },
) {
    private val activeReloads = linkedMapOf<Long, Deferred<Boolean>>()
    private var nextToken = 0L

    fun hasPendingReloads(): Boolean = activeReloads.isNotEmpty()

    fun cancelAll() {
        val pending = activeReloads.values.toList()
        activeReloads.clear()
        pending.forEach { it.cancel() }
    }

    fun onExternalEntityCreated(
        ready: TransactionUiState.Ready,
        intent: TransactionIntent.ExternalEntityCreated,
    ) {
        val editId = ready.form.existingTransactionId.takeIf { it > 0L }
        val capturedGeneration = currentGeneration()
        val pendingCategoryTarget = if (intent.kind == ExternalEntityKind.CATEGORY) {
            TransactionExternalEntityHelper.consumePendingCategoryTarget(savedStateHandle)
                ?: TransactionExternalEntityHelper.captureCategoryTarget(ready)
        } else {
            null
        }
        val token = ++nextToken
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            try {
                executeReloadAndApply(
                    editId = editId,
                    capturedGeneration = capturedGeneration,
                    intent = intent,
                    pendingCategoryTarget = pendingCategoryTarget,
                )
            } finally {
                activeReloads.remove(token)
            }
        }
        activeReloads[token] = deferred
        deferred.start()
    }

    suspend fun awaitPendingReloads(): Boolean {
        var allSucceeded = true
        while (activeReloads.isNotEmpty()) {
            val pending = activeReloads.values.toList()
            for (reload in pending) {
                if (!reload.await()) {
                    allSucceeded = false
                }
            }
        }
        return allSucceeded
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeReloadAndApply(
        editId: Long?,
        capturedGeneration: Long,
        intent: TransactionIntent.ExternalEntityCreated,
        pendingCategoryTarget: PendingExternalCategoryTarget?,
    ): Boolean {
        val catalog = try {
            TransactionActionRunner.runOn(ioDispatcher) {
                ports.repository.loadCatalog(editingTransactionId = editId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload catalog after external %s creation", intent.kind)
            if (currentGeneration() == capturedGeneration) {
                sendEffect(
                    TransactionEffect.ShowMessage(
                        e.localizedMessage?.takeIf { it.isNotBlank() } ?: DEFAULT_LOAD_ERROR_MESSAGE
                    )
                )
            }
            null
        }
        var applied = false
        if (catalog != null && currentGeneration() == capturedGeneration) {
            // Do not gate on !isSaving: handleSave sets isSaving = true while awaiting pending reloads.
            val latestReady = currentReady()
            if (latestReady != null) {
                val updated = TransactionExternalEntityHelper.applyExternalEntityCreated(
                    ready = latestReady,
                    refreshedCatalog = catalog,
                    kind = intent.kind,
                    createdId = intent.createdId,
                    pendingCategoryTarget = pendingCategoryTarget,
                )
                publishReady(updated)
                applied = true
            }
        }
        return applied
    }

    private companion object {
        const val DEFAULT_LOAD_ERROR_MESSAGE = "Failed to load transaction."
    }
}
