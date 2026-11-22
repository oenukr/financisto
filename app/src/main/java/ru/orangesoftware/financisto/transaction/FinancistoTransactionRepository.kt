// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.rates.ExchangeRate
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.LoadedTransactionRecord
import ru.orangesoftware.financisto.transaction.model.SaveTransactionCommand
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionEditLoadResult
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.port.TransactionRepository
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import kotlin.math.abs

class FinancistoTransactionRepository(
    private val context: Context,
    private val db: DatabaseAdapter,
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransactionRepository {

    override suspend fun loadCatalog(editingTransactionId: Long?): TransactionCatalog = withContext(ioDispatcher) {
        val editedTx = editingTransactionId?.takeIf { it > 0L }?.let { loadNormalizedParent(it) }
        TransactionCatalog(
            accounts = FinancistoTransactionMapper.loadAccounts(db, editedTx),
            currencies = FinancistoTransactionMapper.loadCurrencies(db),
            categories = FinancistoTransactionMapper.loadCategories(db),
            payees = FinancistoTransactionMapper.loadPayees(db, editedTx),
            projects = FinancistoTransactionMapper.loadProjects(db, editedTx),
            locations = FinancistoTransactionMapper.loadLocations(db, editedTx),
            recentNotes = FinancistoTransactionMapper.queryRecentNotes(db),
        )
    }

    override suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult = withContext(ioDispatcher) {
        val tx = loadNormalizedParent(transactionId)
        val splits = tx?.splits.orEmpty()
        when {
            tx == null -> TransactionEditLoadResult.NotFound(transactionId)
            requiresLegacyFallback(tx, splits) -> TransactionEditLoadResult.RequiresLegacyFallback(tx.id, tx.isTransfer)
            else -> TransactionEditLoadResult.Loaded(mapToLoadedRecord(tx, splits))
        }
    }

    override suspend fun getLatestExchangeRate(
        fromCurrencyId: Long,
        toCurrencyId: Long,
    ): Double? = withContext(ioDispatcher) {
        val validPair = fromCurrencyId > 0L && toCurrencyId > 0L && fromCurrencyId != toCurrencyId
        val fromCurrency = if (validPair) CurrencyCache.getCurrency(db, fromCurrencyId) else null
        val toCurrency = if (fromCurrency != null) CurrencyCache.getCurrency(db, toCurrencyId) else null
        if (fromCurrency == null || toCurrency == null) {
            null
        } else {
            val rate = db.latestRates.getRate(fromCurrency, toCurrency)
            rate.takeIf { it != ExchangeRate.NA && it.isOk && it.rate > 0.0 }?.rate
        }
    }

    override suspend fun saveTransaction(command: SaveTransactionCommand): Long = withContext(ioDispatcher) {
        val isNew = command.existingTransactionId <= 0L
        val tx = if (isNew) Transaction() else loadNormalizedParent(command.existingTransactionId) ?: Transaction()
        tx.isTemplate = 0
        tx.fromAccountId = command.fromAccountId
        tx.toAccountId = if (command.mode == TransactionMode.TRANSFER) command.toAccountId else 0L
        tx.categoryId = command.categoryId
        tx.payeeId = resolveOrCreatePayee(command.payeeId, command.pendingPayeeName)
        tx.projectId = resolveOrCreateProject(command.projectId, command.pendingProjectName)
        tx.locationId = command.locationId
        tx.fromAmount = command.fromAmountMinor
        tx.toAmount = if (command.mode == TransactionMode.TRANSFER) command.toAmountMinor else 0L
        tx.originalCurrencyId = command.originalCurrencyId
        tx.originalFromAmount = command.originalFromAmountMinor
        tx.note = command.note
        tx.dateTime = command.dateTimeMillis
        tx.status = FinancistoTransactionMapper.toDomainStatus(command.status)
        tx.attachedPicture = command.attachedPicture
        tx.isCCardPayment = if (command.isCCardPayment) 1 else 0
        tx.splits = buildSplitTransactions(command, tx.status)
        val savedId = db.insertOrUpdate(tx, emptyList())
        if (savedId > 0L) {
            if (isNew) {
                MyPreferences.setLastAccount(context, tx.fromAccountId)
            }
            AccountWidget.updateWidgets(context)
        }
        savedId
    }

    private fun requiresLegacyFallback(tx: Transaction, splits: List<Transaction>): Boolean {
        val hasUnsupportedSplit = splits.any { it.isTransfer }
        val hasParentAttrs = db.getAllAttributesForTransaction(tx.id).isNotEmpty() ||
            (tx.categoryId > 0L && db.getAttributesForCategory(tx.categoryId).isNotEmpty())
        val hasSplitAttrs = splits.any { split ->
            db.getAllAttributesForTransaction(split.id).isNotEmpty() ||
                (split.categoryId > 0L && db.getAttributesForCategory(split.categoryId).isNotEmpty())
        }
        val hasAttrs = hasParentAttrs || hasSplitAttrs
        return tx.isTemplateLike() || hasUnsupportedSplit || hasAttrs
    }

    private fun mapToLoadedRecord(tx: Transaction, splits: List<Transaction>): LoadedTransactionRecord {
        val mode = when {
            tx.isTransfer -> TransactionMode.TRANSFER
            tx.fromAmount > 0L -> TransactionMode.INCOME
            else -> TransactionMode.EXPENSE
        }
        val editableSplits = splits.mapIndexed { idx, split ->
            val rawMinor = if (tx.originalCurrencyId > 0L) split.originalFromAmount else split.fromAmount
            EditableSplit(
                localId = -(idx + 1L),
                categoryId = split.categoryId,
                amountMinor = abs(rawMinor),
                isIncome = rawMinor > 0L,
                note = split.note.orEmpty(),
                projectId = split.projectId,
            )
        }
        return LoadedTransactionRecord(
            id = tx.id,
            mode = mode,
            fromAccountId = tx.fromAccountId,
            toAccountId = tx.toAccountId,
            categoryId = tx.categoryId,
            payeeId = tx.payeeId,
            projectId = tx.projectId,
            locationId = tx.locationId,
            fromAmountMinor = tx.fromAmount,
            toAmountMinor = tx.toAmount,
            originalCurrencyId = tx.originalCurrencyId,
            originalFromAmountMinor = tx.originalFromAmount,
            note = tx.note.orEmpty(),
            dateTimeMillis = tx.dateTime,
            status = FinancistoTransactionMapper.toOption(tx.status),
            attachedPicture = tx.attachedPicture,
            isCCardPayment = tx.isCreditCardPayment(),
            splits = editableSplits,
        )
    }

    private fun buildSplitTransactions(
        command: SaveTransactionCommand,
        parentStatus: TransactionStatus,
    ): List<Transaction>? {
        return command.splits.takeIf { it.isNotEmpty() }?.map { split ->
            Transaction().apply {
                categoryId = split.categoryId
                fromAmount = split.amountMinor
                if (command.originalCurrencyId > 0L) {
                    originalCurrencyId = command.originalCurrencyId
                    originalFromAmount = split.originalFromAmountMinor
                }
                note = split.note
                projectId = split.projectId
                status = parentStatus
            }
        }
    }

    private fun loadNormalizedParent(transactionId: Long): Transaction? {
        val loaded = db.getTransaction(transactionId)
        return when {
            loaded.id <= 0L -> null
            loaded.isSplitChild -> db.getTransaction(loaded.parentId).takeIf { it.id > 0L }
            else -> loaded
        }
    }

    private fun resolveOrCreatePayee(payeeId: Long, pendingName: String?): Long {
        val trimmed = pendingName?.trim().orEmpty()
        return when {
            payeeId > 0L -> payeeId
            trimmed.isEmpty() -> TransactionIds.NO_PAYEE_ID
            else -> {
                val existing = db.allPayeeList.firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
                existing?.id ?: db.saveOrUpdate(Payee().apply { title = trimmed })
            }
        }
    }

    private fun resolveOrCreateProject(projectId: Long, pendingName: String?): Long {
        val trimmed = pendingName?.trim().orEmpty()
        return when {
            projectId > 0L -> projectId
            trimmed.isEmpty() -> TransactionIds.NO_PROJECT_ID
            else -> {
                val existing = db.getAllProjectsList(false).firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
                existing?.id ?: db.saveOrUpdate(Project().apply { title = trimmed })
            }
        }
    }
}
