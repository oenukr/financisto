// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.database.sqlite.SQLiteException
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.LocationOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import timber.log.Timber

internal object FinancistoTransactionMapper {
    internal const val STORAGE_DECIMALS = 2
    private val POW10 = longArrayOf(1L, 10L, 100L, 1_000L, 10_000L)
    private const val RECENT_NOTES_LIMIT = 8
    private val RECENT_NOTES_SQL = """
        SELECT DISTINCT TRIM(${DatabaseHelper.TransactionColumns.note}) AS n
        FROM ${DatabaseHelper.TRANSACTION_TABLE}
        WHERE ${DatabaseHelper.TransactionColumns.parent_id} = 0
          AND ${DatabaseHelper.TransactionColumns.is_template} = 0
          AND ${DatabaseHelper.TransactionColumns.note} IS NOT NULL
          AND TRIM(${DatabaseHelper.TransactionColumns.note}) <> ''
        ORDER BY ${DatabaseHelper.TransactionColumns.datetime} DESC
        LIMIT ?
    """.trimIndent()

    fun loadAccounts(db: DatabaseAdapter, editedTx: Transaction?): List<AccountOption> {
        val requiredIds = setOfNotNull(
            editedTx?.fromAccountId?.takeIf { it > 0L },
            editedTx?.toAccountId?.takeIf { it > 0L },
        )
        return db.allAccountsList
            .filter { it.id > 0L && (it.isActive || it.id in requiredIds) }
            .map { a ->
                val cur = a.currency ?: Currency.EMPTY
                AccountOption(
                    id = a.id,
                    title = a.title.orEmpty(),
                    typeLabel = a.type.orEmpty(),
                    currencyId = cur.id,
                    currencyCode = cur.name.orEmpty(),
                    currencySymbol = cur.symbol.orEmpty(),
                    currencyDecimals = cur.decimals,
                    balanceMinor = rescaleMinorUnits(a.totalAmount, STORAGE_DECIMALS, cur.decimals),
                    lastCategoryId = a.lastCategoryId,
                    lastAccountId = a.lastAccountId,
                    isActive = a.isActive,
                )
            }
    }

    fun loadCurrencies(db: DatabaseAdapter): List<CurrencyOption> {
        return db.allCurrenciesList.map { c ->
            CurrencyOption(
                id = c.id,
                code = c.name.orEmpty(),
                symbol = c.symbol.orEmpty(),
                decimals = c.decimals,
                isDefault = c.isDefault,
            )
        }
    }

    fun loadPayees(db: DatabaseAdapter, editedTx: Transaction?): List<PayeeOption> {
        val requiredId = editedTx?.payeeId?.takeIf { it > 0L }
        return db.allPayeeList
            .filter { it.id > 0L && (it.isActive || it.id == requiredId) }
            .map { p ->
                PayeeOption(
                    id = p.id,
                    title = p.title.orEmpty(),
                    lastCategoryId = p.lastCategoryId,
                    isActive = p.isActive,
                )
            }
    }

    fun loadProjects(db: DatabaseAdapter, editedTx: Transaction?): List<ProjectOption> {
        val requiredIds = buildSet {
            editedTx?.projectId?.takeIf { it > 0L }?.let(::add)
            editedTx?.splits?.mapNotNull { it.projectId.takeIf { id -> id > 0L } }?.forEach(::add)
        }
        return db.getAllProjectsList(false)
            .filter { it.id > 0L && (it.isActive || it.id in requiredIds) }
            .map { ProjectOption(id = it.id, title = it.title.orEmpty(), isActive = it.isActive) }
    }

    fun loadLocations(db: DatabaseAdapter, editedTx: Transaction?): List<LocationOption> {
        val requiredId = editedTx?.locationId?.takeIf { it > 0L }
        return db.getAllLocationsList(false)
            .filter { it.id > 0L && (it.isActive || it.id == requiredId) }
            .map { loc -> LocationOption(id = loc.id, title = loc.title.orEmpty(), isActive = loc.isActive) }
    }

    private class CategoryStackEntry(
        val category: Category,
        val parentPath: String?,
        val parentId: Long,
    )

    fun loadCategories(db: DatabaseAdapter): List<CategoryOption> {
        val categories = mutableListOf<CategoryOption>()
        val stack = mutableListOf<CategoryStackEntry>()
        val roots = db.getCategoriesTree(false).roots
        for (i in roots.indices.reversed()) {
            stack.add(CategoryStackEntry(roots[i], null, 0L))
        }
        while (stack.isNotEmpty()) {
            val entry = stack.removeAt(stack.lastIndex)
            val cat = entry.category
            if (cat.id <= 0L) continue
            val savedLevel = cat.level
            cat.level = 0
            val title = cat.title.orEmpty()
            cat.level = savedLevel
            val fullPath = if (entry.parentPath.isNullOrBlank()) title else "${entry.parentPath} > $title"
            categories.add(
                CategoryOption(
                    id = cat.id,
                    parentId = entry.parentId,
                    title = title,
                    fullPath = fullPath,
                    level = savedLevel,
                    isIncome = cat.isIncome,
                    lastLocationId = cat.lastLocationId,
                    lastProjectId = cat.lastProjectId,
                )
            )
            if (cat.hasChildren()) {
                val children = cat.children.roots
                for (i in children.indices.reversed()) {
                    stack.add(CategoryStackEntry(children[i], fullPath, cat.id))
                }
            }
        }
        return categories
    }

    fun queryRecentNotes(db: DatabaseAdapter): List<String> {
        return try {
            val sqlDb = db.db()
            val notes = mutableListOf<String>()
            sqlDb.rawQuery(RECENT_NOTES_SQL, arrayOf(RECENT_NOTES_LIMIT.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }?.let(notes::add)
                }
            }
            notes
        } catch (e: SQLiteException) {
            Timber.w(e, "Failed to query recent notes")
            emptyList()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Database unavailable for recent notes")
            emptyList()
        }
    }

    fun toOption(status: TransactionStatus): TransactionStatusOption = when (status) {
        TransactionStatus.RS -> TransactionStatusOption.RS
        TransactionStatus.PN -> TransactionStatusOption.PN
        TransactionStatus.UR -> TransactionStatusOption.UR
        TransactionStatus.CLEARED -> TransactionStatusOption.CLEARED
        TransactionStatus.RECONCILED -> TransactionStatusOption.RECONCILED
    }

    fun toDomainStatus(status: TransactionStatusOption): TransactionStatus = when (status) {
        TransactionStatusOption.RS -> TransactionStatus.RS
        TransactionStatusOption.PN -> TransactionStatus.PN
        TransactionStatusOption.UR -> TransactionStatus.UR
        TransactionStatusOption.CLEARED -> TransactionStatus.CLEARED
        TransactionStatusOption.RECONCILED -> TransactionStatus.RECONCILED
    }

    fun rescaleMinorUnits(amount: Long, fromDecimals: Int, toDecimals: Int): Long {
        val from = fromDecimals.coerceIn(0, POW10.lastIndex)
        val to = toDecimals.coerceIn(0, POW10.lastIndex)
        return when {
            from == to -> amount
            from < to -> amount * POW10[to - from]
            else -> {
                val factor = POW10[from - to]
                val half = factor / 2L
                if (amount >= 0L) (amount + half) / factor else (amount - half) / factor
            }
        }
    }
}
