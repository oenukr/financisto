// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.app

import android.media.ExifInterface
import android.net.Uri
import ru.orangesoftware.financisto.appfunctions.AccountInfo
import ru.orangesoftware.financisto.appfunctions.AppFunctionDatabase
import ru.orangesoftware.financisto.appfunctions.CategoryInfo
import ru.orangesoftware.financisto.appfunctions.HistoricalCategorySignal
import ru.orangesoftware.financisto.appfunctions.PayeeInfo
import ru.orangesoftware.financisto.appfunctions.TransactionSplit
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AppFunctionDatabaseImpl(private val db: DatabaseAdapter) : AppFunctionDatabase {

    override fun getAccounts(): List<AccountInfo> {
        return db.allAccountsList
            .filter { it.id > 0L && it.isActive }
            .map { account ->
                AccountInfo(
                    id = account.id,
                    title = account.title.orEmpty(),
                    currencyCode = account.currency?.name.orEmpty(),
                    type = account.type.orEmpty(),
                    lastCategoryId = account.lastCategoryId,
                )
            }
    }

    override fun getCategories(): List<CategoryInfo> {
        val result = mutableListOf<CategoryInfo>()
        collectCategories(
            categories = db.getCategoriesTree(false).roots,
            parentPath = null,
            parentId = 0L,
            destination = result,
        )
        return result
    }

    override fun getPayees(): List<PayeeInfo> {
        return db.allActivePayeeList
            .filter { it.id > 0L && !it.title.isNullOrBlank() }
            .map { payee ->
                PayeeInfo(
                    id = payee.id,
                    title = payee.title.orEmpty(),
                    lastCategoryId = payee.lastCategoryId,
                )
            }
    }

    override fun getCategoryHistory(limit: Int): List<HistoricalCategorySignal> {
        val sqlDb = if (limit > 0) runCatching { db.db() }.getOrNull() else null
        if (sqlDb == null) return emptyList()
        val history = mutableListOf<HistoricalCategorySignal>()
        sqlDb.rawQuery(CATEGORY_HISTORY_QUERY, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                history.add(
                    HistoricalCategorySignal(
                        categoryId = cursor.getLong(0),
                        payeeId = cursor.getLong(1).coerceAtLeast(0L),
                        note = cursor.getString(2).orEmpty(),
                    )
                )
            }
        }
        return history
    }

    override fun createPayee(name: String): Long {
        val payee = Payee().apply { title = name }
        return db.saveOrUpdate(payee)
    }

    override fun saveReceiptPicture(uri: Uri): String? {
        return runCatching {
            openUriStream(uri)?.use { input ->
                val targetFile = PicturesUtil.createEmptyImageFile()
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                targetFile.name
            }
        }.getOrNull()
    }

    override fun extractReceiptImageDateTime(uri: Uri): LocalDateTime? {
        return runCatching {
            openUriStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                parseExifDateTime(rawDate)
            }
        }.getOrNull()
    }

    override fun insertOrUpdateTransaction(
        fromAccountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        payeeId: Long?,
        amount: Long,
        toAmount: Long?,
        note: String,
        dateTime: Long,
        attachedPicture: String?,
        splits: List<TransactionSplit>,
    ): Long {
        val transaction = Transaction().apply {
            this.isTemplate = 0
            this.fromAccountId = fromAccountId
            this.toAccountId = toAccountId ?: 0L
            this.categoryId = if (splits.isNotEmpty()) Category.SPLIT_CATEGORY_ID else (categoryId ?: 0L)
            this.payeeId = payeeId ?: 0L
            this.fromAmount = amount
            this.toAmount = toAmount ?: 0L
            this.note = note
            this.dateTime = dateTime
            this.attachedPicture = attachedPicture
            this.status = TransactionStatus.UR
            if (splits.isNotEmpty()) {
                this.splits = splits.map { split ->
                    Transaction().apply {
                        this.categoryId = split.categoryId
                        this.fromAmount = split.amount
                        this.note = split.note
                        this.status = TransactionStatus.UR
                    }
                }
            }
        }
        return db.insertOrUpdate(transaction)
    }

    private fun collectCategories(
        categories: List<Category>,
        parentPath: String?,
        parentId: Long,
        destination: MutableList<CategoryInfo>,
    ) {
        for (category in categories) {
            if (category.id > 0L) {
                val savedLevel = category.level
                category.level = 0
                val rawTitle = category.title.orEmpty()
                category.level = savedLevel
                val fullPath = if (parentPath.isNullOrBlank()) rawTitle else "$parentPath > $rawTitle"
                destination.add(
                    CategoryInfo(
                        id = category.id,
                        title = rawTitle,
                        fullPath = fullPath,
                        isIncome = category.isIncome,
                        parentId = parentId,
                    )
                )
                if (category.hasChildren()) {
                    collectCategories(
                        categories = category.children.roots,
                        parentPath = fullPath,
                        parentId = category.id,
                        destination = destination,
                    )
                }
            }
        }
    }

    private fun openUriStream(uri: Uri): InputStream? {
        val contentResolverStream = runCatching {
            db.context?.contentResolver?.openInputStream(uri)
        }.getOrNull()
        val path = uri.path
        return contentResolverStream ?: if (!path.isNullOrBlank()) {
            runCatching { FileInputStream(File(path)) }.getOrNull()
        } else {
            null
        }
    }

    internal companion object {
        private val CATEGORY_HISTORY_QUERY = """
            SELECT t.category_id,
                   CASE WHEN t.payee_id > 0 THEN t.payee_id ELSE COALESCE(p.payee_id, 0) END,
                   COALESCE(t.note, '')
            FROM ${DatabaseHelper.TRANSACTION_TABLE} t
            LEFT JOIN ${DatabaseHelper.TRANSACTION_TABLE} p ON t.parent_id = p._id
            WHERE t.category_id > 0 AND t.is_template = 0
            ORDER BY t.datetime DESC, t._id DESC
            LIMIT ?
        """.trimIndent()

        private val EXIF_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )

        internal fun parseExifDateTime(rawDate: String?): LocalDateTime? {
            val trimmed = rawDate?.trim().orEmpty()
            return if (trimmed.isEmpty()) {
                null
            } else {
                EXIF_FORMATTERS.firstNotNullOfOrNull { formatter ->
                    runCatching { LocalDateTime.parse(trimmed, formatter) }.getOrNull()
                }
            }
        }
    }
}
