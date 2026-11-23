// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.mvi

import kotlinx.coroutines.CompletableDeferred
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.LoadedTransactionRecord
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.transaction.port.TransactionRepository
import kotlin.time.Clock
import kotlin.time.Instant

internal object TransactionTestFakes {
    const val FIXED_NOW_MILLIS = 1_700_000_000_000L

    val fixedClock: Clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(FIXED_NOW_MILLIS)
    }

    fun createPorts(
        repository: TransactionRepository,
        draftStore: DraftSessionStore,
        exchangeRatePort: ExchangeRatePort,
        mediaPort: ReceiptMediaPort,
        prefs: TransactionFormPreferences = TransactionFormPreferences(),
    ): TransactionPorts {
        return TransactionPorts(
            repository = repository,
            preferencesPort = object : TransactionPreferencesPort {
                override fun readFormPreferences(): TransactionFormPreferences = prefs
            },
            draftSessionStore = draftStore,
            exchangeRatePort = exchangeRatePort,
            receiptMediaPort = mediaPort,
        )
    }

    fun sampleRecord(
        id: Long,
        fromAccountId: Long = 1L,
        payeeId: Long = 0L,
        projectId: Long = 0L,
        attachedPicture: String? = null,
    ): LoadedTransactionRecord {
        return LoadedTransactionRecord(
            id = id,
            mode = TransactionMode.EXPENSE,
            fromAccountId = fromAccountId,
            toAccountId = 0L,
            categoryId = 10L,
            payeeId = payeeId,
            projectId = projectId,
            locationId = 0L,
            fromAmountMinor = -4200L,
            toAmountMinor = 0L,
            originalCurrencyId = 0L,
            originalFromAmountMinor = 0L,
            note = "Legacy note",
            dateTimeMillis = FIXED_NOW_MILLIS,
            status = TransactionStatusOption.CLEARED,
            attachedPicture = attachedPicture,
            isCCardPayment = false,
            splits = emptyList(),
        )
    }

    fun defaultCatalog(): TransactionCatalog {
        return TransactionCatalog(
            accounts = listOf(
                AccountOption(
                    id = 1L,
                    title = "Cash",
                    typeLabel = "CASH",
                    currencyId = 1L,
                    currencyCode = "USD",
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    balanceMinor = 50000L,
                ),
            ),
            currencies = listOf(
                CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
            ),
            categories = listOf(
                CategoryOption(id = 10L, parentId = 0L, title = "Food", fullPath = "Food", level = 1, isIncome = false),
            ),
        )
    }

    fun multiCurrencyCatalog(): TransactionCatalog {
        return TransactionCatalog(
            accounts = listOf(
                AccountOption(
                    id = 1L,
                    title = "USD Cash",
                    typeLabel = "CASH",
                    currencyId = 1L,
                    currencyCode = "USD",
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    balanceMinor = 50000L,
                ),
                AccountOption(
                    id = 2L,
                    title = "EUR Bank",
                    typeLabel = "BANK",
                    currencyId = 2L,
                    currencyCode = "EUR",
                    currencySymbol = "€",
                    currencyDecimals = 2,
                    balanceMinor = 40000L,
                ),
            ),
            currencies = listOf(
                CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
                CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
                CurrencyOption(id = 3L, code = "GBP", symbol = "£", decimals = 2),
            ),
            categories = listOf(
                CategoryOption(id = 10L, parentId = 0L, title = "Food", fullPath = "Food", level = 1, isIncome = false),
            ),
        )
    }
}

internal class FakeTransactionRepository(
    var catalog: TransactionCatalog,
) : TransactionRepository {
    var editResult: TransactionEditLoadResult = TransactionEditLoadResult.NotFound(-1L)
    val savedCommands = mutableListOf<SaveTransactionCommand>()
    val storedRates = mutableMapOf<Pair<Long, Long>, Double>()
    val requestedRatePairs = mutableListOf<Pair<Long, Long>>()
    var pendingSaveDeferred: CompletableDeferred<Long>? = null
    var pendingCatalogDeferred: CompletableDeferred<TransactionCatalog>? = null
    var pendingEditDeferred: CompletableDeferred<TransactionEditLoadResult>? = null
    var onLoadCatalog: (suspend () -> Unit)? = null
    var failLoadCatalogMessage: String? = null
    var failLoadCatalogAfterFirstMessage: String? = null
    var loadForEditHandler: (suspend (Long) -> TransactionEditLoadResult)? = null
    var rateHandler: (suspend (Long, Long) -> Double?)? = null
    var failSaveMessage: String? = null
    var lastLoadCatalogEditingTransactionId: Long? = null
    private var nextId = 101L
    private var loadCatalogCalls = 0

    override suspend fun loadCatalog(editingTransactionId: Long?): TransactionCatalog {
        lastLoadCatalogEditingTransactionId = editingTransactionId
        onLoadCatalog?.invoke()
        loadCatalogCalls++
        failLoadCatalogMessage?.let { throw IllegalStateException(it) }
        if (loadCatalogCalls > 1) {
            failLoadCatalogAfterFirstMessage?.let { throw IllegalStateException(it) }
        }
        val deferred = pendingCatalogDeferred
        return if (deferred != null && loadCatalogCalls > 1) deferred.await() else catalog
    }

    override suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult {
        loadForEditHandler?.let { return it(transactionId) }
        return pendingEditDeferred?.await() ?: editResult
    }

    override suspend fun getLatestExchangeRate(fromCurrencyId: Long, toCurrencyId: Long): Double? {
        requestedRatePairs.add(fromCurrencyId to toCurrencyId)
        rateHandler?.let { return it(fromCurrencyId, toCurrencyId) }
        return storedRates[fromCurrencyId to toCurrencyId]
    }

    override suspend fun saveTransaction(command: SaveTransactionCommand): Long {
        savedCommands.add(command)
        failSaveMessage?.let { throw IllegalStateException(it) }
        val deferred = pendingSaveDeferred
        return deferred?.await() ?: nextId++
    }
}

internal class FakeExchangeRatePort : ExchangeRatePort {
    var handler: suspend (Long, Long) -> Result<Double> = { _, _ -> Result.success(1.25) }

    override suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> =
        handler(fromCurrencyId, toCurrencyId)
}

internal class FakeDraftSessionStore : DraftSessionStore {
    val savedByDraftId = mutableMapOf<String, Long>()
    var failMarkDraftSavedMessage: String? = null

    override suspend fun getSavedTransactionId(draftId: String): Long? = savedByDraftId[draftId]

    override suspend fun markDraftSaved(draftId: String, transactionId: Long) {
        failMarkDraftSavedMessage?.let { throw IllegalStateException(it) }
        savedByDraftId[draftId] = transactionId
    }
}

internal class FakeReceiptMediaPort : ReceiptMediaPort {
    val deletedFiles = mutableListOf<String>()
    var stageHandler: (suspend (String) -> String?)? = null
    var resolveFilePathHandler: ((String) -> String?)? = null
    var failDeleteMessage: String? = null

    override suspend fun stageFromUri(uriString: String): String? {
        val custom = stageHandler
        return if (custom != null) custom(uriString) else "uri_staged.jpg"
    }

    override fun resolveFilePath(fileName: String): String? {
        val custom = resolveFilePathHandler
        return if (custom != null) custom(fileName) else "/tmp/$fileName"
    }

    override suspend fun deleteStagedFile(fileName: String) {
        failDeleteMessage?.let { throw IllegalStateException(it) }
        deletedFiles.add(fileName)
    }
}
