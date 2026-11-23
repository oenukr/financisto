// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.port

import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences

interface TransactionRepository {
    suspend fun loadCatalog(editingTransactionId: Long? = null): TransactionCatalog
    suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult
    suspend fun getLatestExchangeRate(fromCurrencyId: Long, toCurrencyId: Long): Double?
    suspend fun saveTransaction(command: SaveTransactionCommand): Long
}

interface TransactionPreferencesPort {
    fun readFormPreferences(): TransactionFormPreferences
}

interface DraftSessionStore {
    suspend fun getSavedTransactionId(draftId: String): Long?
    suspend fun markDraftSaved(draftId: String, transactionId: Long)
    suspend fun acknowledgeDraftOpened(draftId: String) {}
    suspend fun clearPendingDraft(draftId: String) {}
}

interface ExchangeRatePort {
    suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double>
}

interface ReceiptMediaPort {
    suspend fun stageFromUri(uriString: String): String?
    fun resolveFilePath(fileName: String): String?
    suspend fun deleteStagedFile(fileName: String)
}

data class TransactionPorts(
    val repository: TransactionRepository,
    val preferencesPort: TransactionPreferencesPort,
    val draftSessionStore: DraftSessionStore,
    val exchangeRatePort: ExchangeRatePort,
    val receiptMediaPort: ReceiptMediaPort,
)
