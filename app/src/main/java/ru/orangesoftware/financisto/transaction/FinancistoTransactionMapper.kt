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
                    balanceMinor = a.totalAmount,
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

    fun loadCategories(db: DatabaseAdapter): List<CategoryOption> {
        val categories = mutableListOf<CategoryOption>()
        collectCategories(
            nodes = db.getCategoriesTree(false).roots,
            parentPath = null,
            parentId = 0L,
            dest = categories,
        )
        return categories
    }

    fun collectCategories(
        nodes: List<Category>,
        parentPath: String?,
        parentId: Long,
        dest: MutableList<CategoryOption>,
    ) {
        for (cat in nodes) {
            if (cat.id <= 0L) continue
            val savedLevel = cat.level
            cat.level = 0
            val title = cat.title.orEmpty()
            cat.level = savedLevel
            val fullPath = if (parentPath.isNullOrBlank()) title else "$parentPath > $title"
            dest.add(
                CategoryOption(
                    id = cat.id,
                    parentId = parentId,
                    title = title,
                    fullPath = fullPath,
                    level = savedLevel,
                    isIncome = cat.isIncome,
                    lastLocationId = cat.lastLocationId,
                    lastProjectId = cat.lastProjectId,
                )
            )
            if (cat.hasChildren()) {
                collectCategories(cat.children.roots, fullPath, cat.id, dest)
            }
        }
    }

    fun queryRecentNotes(db: DatabaseAdapter): List<String> {
        val sqlDb = try {
            db.db()
        } catch (e: SQLiteException) {
            Timber.w(e, "Failed to open database for recent notes")
            null
        } catch (e: IllegalStateException) {
            Timber.w(e, "Database unavailable for recent notes")
            null
        } ?: return emptyList()
        val notes = mutableListOf<String>()
        sqlDb.rawQuery(RECENT_NOTES_SQL, arrayOf(RECENT_NOTES_LIMIT.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }?.let(notes::add)
            }
        }
        return notes
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
}
