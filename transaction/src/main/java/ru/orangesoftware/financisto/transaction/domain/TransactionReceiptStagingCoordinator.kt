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
) {
    private data class ActiveStaging(
        val generation: Long,
        var job: Job? = null,
    )

    private val activeStagings = LinkedHashMap<Long, ActiveStaging>()
    private var nextToken: Long = 0L

    fun isStagingForCurrentForm(): Boolean {
        val generation = currentGeneration()
        return activeStagings.values.any { it.generation == generation }
    }

    fun cancelAll(clearFlag: Boolean = false) {
        val jobs = activeStagings.values.mapNotNull { it.job }
        activeStagings.clear()
        jobs.forEach(Job::cancel)
        if (clearFlag) {
            syncStagingFlag()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun stageReceiptUri(uriString: String) {
        val capturedGeneration = currentGeneration()
        val token = ++nextToken
        val entry = ActiveStaging(generation = capturedGeneration)
        activeStagings[token] = entry
        syncStagingFlag()

        val job = scope.launch {
            val stagedRef = AtomicReference<String?>(null)
            try {
                TransactionActionRunner.runOn(ioDispatcher) {
                    stagedRef.set(ports.receiptMediaPort.stageFromUri(uriString))
                }
                val staged = stagedRef.get()
                if (staged != null && tryAttachStagedFile(staged, token, capturedGeneration)) {
                    stagedRef.set(null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to stage receipt from URI")
            } finally {
                activeStagings.remove(token)
                syncStagingFlag()
                deleteOrphanIfPresent(stagedRef.getAndSet(null))
            }
        }
        if (activeStagings.containsKey(token)) {
            entry.job = job
        }
    }

    private fun tryAttachStagedFile(
        fileName: String,
        token: Long,
        capturedGeneration: Long,
    ): Boolean {
        val currentReady = currentReady()
        val isStillOwned = activeStagings.containsKey(token) &&
            currentGeneration() == capturedGeneration
        val canAttach = isStillOwned && currentReady != null && !currentReady.isSaving
        if (canAttach) {
            activeStagings.remove(token)
            publishReady(
                currentReady.copy(
                    form = currentReady.form.copy(
                        attachedPictureFileName = fileName,
                        newlyStagedPictureFiles = currentReady.form.newlyStagedPictureFiles + fileName,
                    ),
                    isStagingReceipt = isStagingForCurrentForm(),
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
}
