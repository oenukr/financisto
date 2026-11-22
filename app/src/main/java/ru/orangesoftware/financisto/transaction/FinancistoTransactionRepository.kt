// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.rates.ExchangeRate
import ru.orangesoftware.financisto.transaction.model.CategoryOption
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
) : TransactionRepository {

    override suspend fun loadCatalog(editingTransactionId: Long?): TransactionCatalog {
        val editedTx = editingTransactionId?.takeIf { it > 0L }?.let { loadNormalizedParent(it) }
        val categories = mutableListOf<CategoryOption>()
        FinancistoTransactionMapper.collectCategories(
            nodes = db.getCategoriesTree(false).roots,
            parentPath = null,
            parentId = 0L,
            dest = categories,
        )
        return TransactionCatalog(
            accounts = FinancistoTransactionMapper.loadAccounts(db, editedTx),
            currencies = FinancistoTransactionMapper.loadCurrencies(db),
            categories = categories,
            payees = FinancistoTransactionMapper.loadPayees(db, editedTx),
            projects = FinancistoTransactionMapper.loadProjects(db, editedTx),
            locations = FinancistoTransactionMapper.loadLocations(db, editedTx),
            recentNotes = FinancistoTransactionMapper.queryRecentNotes(db),
        )
    }

    override suspend fun loadForEdit(transactionId: Long): TransactionEditLoadResult {
        val tx = loadNormalizedParent(transactionId) ?: return TransactionEditLoadResult.NotFound(transactionId)
        val splits = tx.splits.orEmpty()
        val hasUnsupportedSplit = splits.any { it.isTransfer }
        val hasParentAttrs = db.getAllAttributesForTransaction(tx.id).isNotEmpty() ||
            (tx.categoryId > 0L && db.getAttributesForCategory(tx.categoryId).isNotEmpty())
        val hasSplitAttrs = splits.any { split ->
            db.getAllAttributesForTransaction(split.id).isNotEmpty() ||
                (split.categoryId > 0L && db.getAttributesForCategory(split.categoryId).isNotEmpty())
        }
        if (tx.isTemplateLike() || hasUnsupportedSplit || hasParentAttrs || hasSplitAttrs) {
            return TransactionEditLoadResult.RequiresLegacyFallback(tx.id, tx.isTransfer)
        }
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
        return TransactionEditLoadResult.Loaded(
            LoadedTransactionRecord(
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
        )
    }

    override suspend fun getLatestExchangeRate(fromCurrencyId: Long, toCurrencyId: Long): Double? {
        if (fromCurrencyId <= 0L || toCurrencyId <= 0L || fromCurrencyId == toCurrencyId) return null
        val fromCurrency = CurrencyCache.getCurrency(db, fromCurrencyId) ?: return null
        val toCurrency = CurrencyCache.getCurrency(db, toCurrencyId) ?: return null
        val rate = db.latestRates.getRate(fromCurrency, toCurrency)
        return if (rate != ExchangeRate.NA && rate.isOk && rate.rate > 0.0) {
            rate.rate
        } else {
            null
        }
    }

    override suspend fun saveTransaction(command: SaveTransactionCommand): Long {
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
        tx.splits = if (command.splits.isEmpty()) {
            null
        } else {
            command.splits.map { s ->
                Transaction().apply {
                    categoryId = s.categoryId
                    fromAmount = s.amountMinor
                    note = s.note
                    projectId = s.projectId
                    status = tx.status
                }
            }
        }
        val savedId = db.insertOrUpdate(tx, emptyList())
        if (savedId > 0L) {
            if (isNew) {
                MyPreferences.setLastAccount(context, tx.fromAccountId)
            }
            AccountWidget.updateWidgets(context)
        }
        return savedId
    }

    private fun loadNormalizedParent(transactionId: Long): Transaction? {
        val loaded = db.getTransaction(transactionId)
        if (loaded.id <= 0L) return null
        return if (loaded.isSplitChild) {
            val parent = db.getTransaction(loaded.parentId)
            if (parent.id > 0L) parent else null
        } else {
            loaded
        }
    }

    private fun resolveOrCreatePayee(payeeId: Long, pendingName: String?): Long {
        if (payeeId > 0L) return payeeId
        val trimmed = pendingName?.trim().orEmpty()
        if (trimmed.isEmpty()) return TransactionIds.NO_PAYEE_ID
        val existing = db.allPayeeList.firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
        return existing?.id ?: db.saveOrUpdate(Payee().apply { title = trimmed })
    }

    private fun resolveOrCreateProject(projectId: Long, pendingName: String?): Long {
        if (projectId > 0L) return projectId
        val trimmed = pendingName?.trim().orEmpty()
        if (trimmed.isEmpty()) return TransactionIds.NO_PROJECT_ID
        val existing = db.getAllProjectsList(false).firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
        return existing?.id ?: db.saveOrUpdate(Project().apply { title = trimmed })
    }
}
