// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.transaction.port.TransactionRepository
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeTransactionActivitySupportTest {

    @Test
    fun loadErrorReplacesIncomingDraftImmediatelyAndHasNullActiveDraftId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val args = TransactionLaunchArgs.AiDraft(
                TransactionDraft(
                    draftId = "failed-draft-1",
                    mode = TransactionMode.EXPENSE,
                    amountMinor = 1000L,
                    fromAccountId = 1L,
                    dateTimeMillis = 1_700_000_000_000L,
                )
            )
            val viewModel = TransactionViewModel(
                launchArgs = args,
                savedStateHandle = SavedStateHandle(),
                ports = portsWithFlakyLoad(failAttempts = 1, mediaPort = RecordingMediaPort()),
                ioDispatcher = dispatcher,
                clock = FixedClock(1_700_000_000_000L),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is TransactionUiState.LoadError)
            assertNull(activeAiDraftId(state, args))
            assertEquals(
                ComposeTransactionActivity.IncomingDraftResolution.REPLACE_IMMEDIATELY,
                resolveIncomingDraftAction("new-draft-2", viewModel, args),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dispatchIntentWhenReadyWaitsThroughLoadErrorAndDispatchesAfterRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val mediaPort = RecordingMediaPort()
            val viewModel = TransactionViewModel(
                launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
                savedStateHandle = SavedStateHandle(),
                ports = portsWithFlakyLoad(failAttempts = 1, mediaPort = mediaPort),
                ioDispatcher = dispatcher,
                clock = FixedClock(1_700_000_000_000L),
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is TransactionUiState.LoadError)

            dispatchIntentWhenReady(
                scope = this,
                viewModel = viewModel,
                receiptMediaPort = mediaPort,
                intent = TransactionIntent.AttachCapturedPhotoFile("retry_recovered.jpg"),
            )
            advanceUntilIdle()
            assertTrue(mediaPort.deletedFiles.isEmpty())

            viewModel.onIntent(TransactionIntent.RetryLoad)
            advanceUntilIdle()

            val ready = viewModel.uiState.value as TransactionUiState.Ready
            assertEquals("retry_recovered.jpg", ready.form.attachedPictureFileName)
            assertTrue(mediaPort.deletedFiles.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dispatchIntentWhenReadyDeletesQueuedPhotoWhenClosedDuringLoadError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val mediaPort = RecordingMediaPort()
            val viewModel = TransactionViewModel(
                launchArgs = TransactionLaunchArgs.BlankNew(accountId = 1L),
                savedStateHandle = SavedStateHandle(),
                ports = portsWithFlakyLoad(failAttempts = 2, mediaPort = mediaPort),
                ioDispatcher = dispatcher,
                clock = FixedClock(1_700_000_000_000L),
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is TransactionUiState.LoadError)

            val activityScope = CoroutineScope(Job() + dispatcher)
            dispatchIntentWhenReady(
                scope = activityScope,
                viewModel = viewModel,
                receiptMediaPort = mediaPort,
                intent = TransactionIntent.AttachCapturedPhotoFile("abandoned_on_error.jpg"),
            )
            advanceUntilIdle()
            assertTrue(mediaPort.deletedFiles.isEmpty())

            activityScope.cancel()
            advanceUntilIdle()

            assertEquals(listOf("abandoned_on_error.jpg"), mediaPort.deletedFiles)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun portsWithFlakyLoad(
        failAttempts: Int,
        mediaPort: ReceiptMediaPort,
    ): TransactionPorts {
        var remainingFailures = failAttempts
        val catalog = TransactionCatalog(
            accounts = listOf(
                AccountOption(
                    id = 1L,
                    title = "Cash",
                    typeLabel = "CASH",
                    currencyId = 1L,
                    currencyCode = "USD",
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    balanceMinor = 0L,
                )
            ),
            currencies = listOf(CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2)),
        )
        return TransactionPorts(
            repository = object : TransactionRepository {
                override suspend fun loadCatalog(editingTransactionId: Long?): TransactionCatalog {
                    if (remainingFailures > 0) {
                        remainingFailures--
                        error("Temporary DB load error")
                    }
                    return catalog
                }

                override suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult =
                    TransactionEditLoadResult.NotFound(transactionId)

                override suspend fun getLatestExchangeRate(fromCurrencyId: Long, toCurrencyId: Long): Double? = null

                override suspend fun saveTransaction(command: SaveTransactionCommand): Long = 1L
            },
            preferencesPort = object : TransactionPreferencesPort {
                override fun readFormPreferences(): TransactionFormPreferences = TransactionFormPreferences()
            },
            draftSessionStore = object : DraftSessionStore {
                override suspend fun getSavedTransactionId(draftId: String): Long? = null
                override suspend fun markDraftSaved(draftId: String, transactionId: Long) = Unit
                override suspend fun acknowledgeDraftOpened(draftId: String) = Unit
                override suspend fun clearPendingDraft(draftId: String) = Unit
            },
            exchangeRatePort = object : ExchangeRatePort {
                override suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> =
                    Result.failure(IllegalStateException("unused"))
            },
            receiptMediaPort = mediaPort,
        )
    }

    private class RecordingMediaPort : ReceiptMediaPort {
        val deletedFiles = mutableListOf<String>()
        override suspend fun stageFromUri(uriString: String): String? = null
        override fun resolveFilePath(fileName: String): String? = null
        override suspend fun deleteStagedFile(fileName: String) {
            deletedFiles.add(fileName)
        }
    }

    private class FixedClock(private val epochMillis: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
    }
}
