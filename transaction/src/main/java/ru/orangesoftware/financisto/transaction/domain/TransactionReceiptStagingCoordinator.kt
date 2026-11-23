// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

internal class TransactionReceiptStagingCoordinator(
    private val scope: CoroutineScope,
    private val ports: TransactionPorts,
    private val currentReady: () -> TransactionUiState.Ready?,
    private val publishReady: (TransactionUiState.Ready) -> Unit,
    private val ioDispatcher: CoroutineDispatcher? = null,
    private val currentGeneration: () -> Long = { 0L },
    private val onError: (String) -> Unit = {},
) {
    private data class ActiveStaging(
        val token: Long,
        val generation: Long,
        var job: Job? = null,
    )

    private var activeStaging: ActiveStaging? = null
    private var latestToken: Long = 0L

    fun isStagingForCurrentForm(): Boolean {
        val current = activeStaging
        return current != null && current.generation == currentGeneration()
    }

    fun cancelAll(clearFlag: Boolean = false) {
        latestToken++
        val job = activeStaging?.job
        activeStaging = null
        job?.cancel()
        if (clearFlag) {
            syncStagingFlag()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun stageReceiptUri(uriString: String) {
        val capturedGeneration = currentGeneration()
        val token = ++latestToken
        val previousJob = activeStaging?.job
        val entry = ActiveStaging(token = token, generation = capturedGeneration)
        activeStaging = entry
        previousJob?.cancel()
        syncStagingFlag()

        val job = scope.launch {
            val stagedRef = AtomicReference<String?>(null)
            var failureMessage: String? = null
            try {
                TransactionActionRunner.runOn(ioDispatcher) {
                    stagedRef.set(ports.receiptMediaPort.stageFromUri(uriString))
                }
                val staged = stagedRef.get()
                if (staged == null) {
                    failureMessage = DEFAULT_STAGE_ERROR_MESSAGE
                } else if (tryAttachStagedFile(staged, token, capturedGeneration)) {
                    stagedRef.set(null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to stage receipt from URI")
                failureMessage = e.localizedMessage?.takeIf { it.isNotBlank() } ?: DEFAULT_STAGE_ERROR_MESSAGE
            } finally {
                val errorToNotify = failureMessage?.takeIf {
                    isCurrentActiveSelection(token, capturedGeneration) && currentReady()?.isSaving != true
                }
                if (activeStaging?.token == token) {
                    activeStaging = null
                }
                syncStagingFlag()
                errorToNotify?.let(onError)
                deleteOrphanIfPresent(stagedRef.getAndSet(null))
            }
        }
        if (activeStaging === entry) {
            entry.job = job
        }
    }

    private fun isCurrentActiveSelection(token: Long, capturedGeneration: Long): Boolean {
        return token == latestToken &&
            activeStaging?.token == token &&
            currentGeneration() == capturedGeneration
    }

    private fun tryAttachStagedFile(
        fileName: String,
        token: Long,
        capturedGeneration: Long,
    ): Boolean {
        val ready = currentReady()
        val canAttach = isCurrentActiveSelection(token, capturedGeneration) &&
            ready != null &&
            !ready.isSaving
        if (canAttach) {
            activeStaging = null
            publishReady(
                ready.copy(
                    form = ready.form.copy(
                        attachedPictureFileName = fileName,
                        newlyStagedPictureFiles = ready.form.newlyStagedPictureFiles + fileName,
                    ),
                    isStagingReceipt = false,
                )
            )
        }
        return canAttach
    }

    private fun syncStagingFlag() {
        val ready = currentReady() ?: return
        val isStaging = isStagingForCurrentForm()
        if (ready.isStagingReceipt != isStaging) {
            publishReady(ready.copy(isStagingReceipt = isStaging))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun deleteOrphanIfPresent(fileName: String?) {
        if (fileName == null) return
        withContext(NonCancellable) {
            try {
                TransactionActionRunner.runOn(ioDispatcher) {
                    ports.receiptMediaPort.deleteStagedFile(fileName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete orphaned staged receipt %s", fileName)
            }
        }
    }

    private companion object {
        const val DEFAULT_STAGE_ERROR_MESSAGE = "Could not attach receipt image."
    }
}
