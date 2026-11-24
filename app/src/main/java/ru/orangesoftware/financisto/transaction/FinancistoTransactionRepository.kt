// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.database.sqlite.SQLiteException
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
import ru.orangesoftware.financisto.transaction.FinancistoTransactionMapper.STORAGE_DECIMALS
import ru.orangesoftware.financisto.transaction.FinancistoTransactionMapper.rescaleMinorUnits
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
        val isTransfer = command.mode == TransactionMode.TRANSFER
        val fromDecimals = accountDecimals(db, command.fromAccountId)
        val toDecimals = if (isTransfer) accountDecimals(db, command.toAccountId) else DEFAULT_DECIMALS
        val origDecimals = currencyDecimals(db, command.originalCurrencyId, fromDecimals)
        val sqlDb = db.db()
        sqlDb.beginTransaction()
        val savedId = try {
            val tx = if (isNew) {
                Transaction()
            } else {
                checkNotNull(loadNormalizedParent(command.existingTransactionId)) {
                    "Transaction ${command.existingTransactionId} no longer exists"
                }
            }
            tx.isTemplate = 0
            tx.fromAccountId = command.fromAccountId
            tx.toAccountId = if (isTransfer) command.toAccountId else 0L
            tx.categoryId = command.categoryId
            tx.payeeId = resolveOrCreatePayee(command.payeeId, command.pendingPayeeName)
            tx.projectId = resolveOrCreateProject(command.projectId, command.pendingProjectName)
            tx.locationId = command.locationId
            tx.fromAmount = rescaleMinorUnits(command.fromAmountMinor, fromDecimals, STORAGE_DECIMALS)
            tx.toAmount = if (isTransfer) rescaleMinorUnits(command.toAmountMinor, toDecimals, STORAGE_DECIMALS) else 0L
            tx.originalCurrencyId = command.originalCurrencyId
            tx.originalFromAmount = if (command.originalCurrencyId > 0L) {
                rescaleMinorUnits(command.originalFromAmountMinor, origDecimals, STORAGE_DECIMALS)
            } else {
                command.originalFromAmountMinor
            }
            tx.note = command.note
            tx.dateTime = command.dateTimeMillis
            tx.status = FinancistoTransactionMapper.toDomainStatus(command.status)
            tx.attachedPicture = command.attachedPicture
            tx.isCCardPayment = if (command.isCCardPayment) 1 else 0
            tx.splits = buildSplitTransactions(command, tx.status)
            val id = db.insertOrUpdateInTransaction(tx, emptyList())
            if (id <= 0L) throw SQLiteException("Failed to persist transaction")
            sqlDb.setTransactionSuccessful()
            id
        } finally {
            sqlDb.endTransaction()
        }
        if (savedId > 0L) {
            if (isNew) MyPreferences.setLastAccount(context, command.fromAccountId)
            TransactionEntryRouter.notifyTransactionSaved()
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
        val fromDecimals = accountDecimals(db, tx.fromAccountId)
        val toDecimals = if (tx.isTransfer) accountDecimals(db, tx.toAccountId) else DEFAULT_DECIMALS
        val hasForeign = tx.originalCurrencyId > 0L
        val origDecimals = currencyDecimals(db, tx.originalCurrencyId, fromDecimals)
        val splitDecimals = if (hasForeign) origDecimals else fromDecimals
        val mode = when {
            tx.isTransfer -> TransactionMode.TRANSFER
            tx.fromAmount > 0L -> TransactionMode.INCOME
            else -> TransactionMode.EXPENSE
        }
        val editableSplits = splits.mapIndexed { idx, split ->
            val rawStorage = if (hasForeign) split.originalFromAmount else split.fromAmount
            val rawMinor = rescaleMinorUnits(rawStorage, STORAGE_DECIMALS, splitDecimals)
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
            fromAmountMinor = rescaleMinorUnits(tx.fromAmount, STORAGE_DECIMALS, fromDecimals),
            toAmountMinor = if (tx.isTransfer) rescaleMinorUnits(tx.toAmount, STORAGE_DECIMALS, toDecimals) else 0L,
            originalCurrencyId = tx.originalCurrencyId,
            originalFromAmountMinor = if (hasForeign) {
                rescaleMinorUnits(tx.originalFromAmount, STORAGE_DECIMALS, origDecimals)
            } else {
                0L
            },
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
        val fromDecimals = accountDecimals(db, command.fromAccountId)
        val origDecimals = currencyDecimals(db, command.originalCurrencyId, fromDecimals)
        return command.splits.takeIf { it.isNotEmpty() }?.map { split ->
            Transaction().apply {
                categoryId = split.categoryId
                fromAmount = rescaleMinorUnits(split.amountMinor, fromDecimals, STORAGE_DECIMALS)
                if (command.originalCurrencyId > 0L) {
                    originalCurrencyId = command.originalCurrencyId
                    originalFromAmount = rescaleMinorUnits(
                        split.originalFromAmountMinor,
                        origDecimals,
                        STORAGE_DECIMALS,
                    )
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

    private companion object {
        private const val DEFAULT_DECIMALS = 2

        private fun accountDecimals(db: DatabaseAdapter, accountId: Long): Int {
            val account = if (accountId > 0L) db.getAccount(accountId) else null
            return account?.currency?.decimals ?: DEFAULT_DECIMALS
        }

        private fun currencyDecimals(db: DatabaseAdapter, currencyId: Long, fallback: Int): Int {
            val currency = if (currencyId > 0L) CurrencyCache.getCurrency(db, currencyId) else null
            return currency?.decimals ?: fallback
        }
    }
}
