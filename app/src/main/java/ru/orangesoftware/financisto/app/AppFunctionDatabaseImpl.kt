// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.app

import android.app.PendingIntent
import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.media.ExifInterface
import android.net.Uri
import androidx.core.net.toUri
import ru.orangesoftware.financisto.appfunctions.AccountInfo
import ru.orangesoftware.financisto.appfunctions.AppFunctionDatabase
import ru.orangesoftware.financisto.appfunctions.AppFunctionTransactionDraft
import ru.orangesoftware.financisto.appfunctions.CategoryInfo
import ru.orangesoftware.financisto.appfunctions.HistoricalCategorySignal
import ru.orangesoftware.financisto.appfunctions.PayeeInfo
import ru.orangesoftware.financisto.appfunctions.TransactionSplit
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.transaction.FinancistoDraftSessionStore
import ru.orangesoftware.financisto.transaction.FinancistoTransactionMapper
import ru.orangesoftware.financisto.transaction.FinancistoTransactionMapper.STORAGE_DECIMALS
import ru.orangesoftware.financisto.transaction.FinancistoTransactionMapper.rescaleMinorUnits
import ru.orangesoftware.financisto.transaction.TransactionEntryRouter
import ru.orangesoftware.financisto.transaction.model.DraftSplit
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.rescaleSplitMinorUnits
import ru.orangesoftware.financisto.utils.PicturesUtil
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

class AppFunctionDatabaseImpl(
    private val db: DatabaseAdapter,
    private val draftSessionStore: FinancistoDraftSessionStore,
) : AppFunctionDatabase {

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
                    currencyId = account.currency?.id ?: 0L,
                )
            }
    }

    override fun getCategories(): List<CategoryInfo> {
        return FinancistoTransactionMapper.loadCategories(db).map { category ->
            CategoryInfo(
                id = category.id,
                title = category.title,
                fullPath = category.fullPath,
                isIncome = category.isIncome,
                parentId = category.parentId,
            )
        }
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
        val sqlDb = if (limit > 0) {
            try {
                db.db()
            } catch (e: SQLiteException) {
                Timber.w(e, "Failed to open database for category history")
                null
            } catch (e: IllegalStateException) {
                Timber.w(e, "Database unavailable for category history")
                null
            }
        } else {
            null
        } ?: return emptyList()
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

    override fun saveReceiptPicture(uri: Uri): String? {
        var targetFile: File? = null
        return try {
            openUriStream(uri)?.use { input ->
                val createdFile = PicturesUtil.createEmptyImageFile()
                targetFile = createdFile
                createdFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                createdFile.name
            }
        } catch (e: IOException) {
            deletePartialReceipt(targetFile)
            Timber.w(e, "Failed to save receipt picture from URI: %s", uri)
            null
        } catch (e: SecurityException) {
            deletePartialReceipt(targetFile)
            Timber.w(e, "No permission to read receipt picture URI: %s", uri)
            null
        } catch (e: UnsupportedOperationException) {
            deletePartialReceipt(targetFile)
            Timber.w(e, "Unsupported receipt picture URI: %s", uri)
            null
        } catch (e: IllegalArgumentException) {
            deletePartialReceipt(targetFile)
            Timber.w(e, "Invalid receipt picture URI: %s", uri)
            null
        }
    }

    override fun extractReceiptImageDateTime(uri: Uri): LocalDateTime? {
        return try {
            openUriStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                parseExifDateTime(rawDate)
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to read EXIF date from receipt URI: %s", uri)
            null
        } catch (e: SecurityException) {
            Timber.w(e, "No permission to read EXIF date from receipt URI: %s", uri)
            null
        }
    }

    override fun createTransactionReviewPendingIntent(draft: AppFunctionTransactionDraft): PendingIntent {
        val context = requireNotNull(db.context) { "DatabaseAdapter context is required to create PendingIntent." }
        val toAccountId = draft.toAccountId
        val mode = when {
            toAccountId != null && toAccountId > 0L -> TransactionMode.TRANSFER
            draft.isIncome -> TransactionMode.INCOME
            else -> TransactionMode.EXPENSE
        }
        val fromDecimals = draft.fromAccountId.takeIf { it > 0L }
            ?.let { db.getAccount(it)?.currency?.decimals } ?: DEFAULT_DECIMALS
        val toDecimals = toAccountId?.takeIf { it > 0L }
            ?.let { db.getAccount(it)?.currency?.decimals } ?: DEFAULT_DECIMALS
        val amountMinor = rescaleMinorUnits(abs(draft.amount), STORAGE_DECIMALS, fromDecimals)
        val toAmountMinor = draft.toAmount?.let { rescaleMinorUnits(abs(it), STORAGE_DECIMALS, toDecimals) }
        val signedTotalMinor = if (mode == TransactionMode.INCOME) amountMinor else -amountMinor
        val uiDraft = TransactionDraft(
            draftId = draft.draftId,
            mode = mode,
            amountMinor = amountMinor,
            fromAccountId = draft.fromAccountId,
            toAccountId = toAccountId,
            toAmountMinor = toAmountMinor,
            categoryId = draft.categoryId,
            payeeId = draft.payeeId,
            pendingPayeeName = if (draft.payeeId == null) draft.payeeName else null,
            dateTimeMillis = draft.dateTime,
            note = draft.note,
            stagedReceiptFileName = draft.attachedPicture,
            splits = scaleDraftSplits(draft.splits, fromDecimals, signedTotalMinor),
            receiptAttachFailed = draft.receiptAttachFailed,
        )
        // Save as pending auto-open fallback when the user switches to Financisto manually;
        // the AppFunctions host invokes the returned PendingIntent as the primary launch channel.
        if (!draftSessionStore.savePendingDraft(uiDraft)) {
            Timber.w("Failed to save pending auto-open fallback for AI draft: %s", uiDraft.draftId)
        }
        val intent = TransactionEntryRouter.createAiDraftIntent(context, uiDraft).apply {
            data = "financisto://ai-draft/${Uri.encode(draft.draftId)}".toUri()
        }
        return PendingIntent.getActivity(
            context,
            draft.draftId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun scaleDraftSplits(
        splits: List<TransactionSplit>,
        fromDecimals: Int,
        signedTotalMinor: Long,
    ): List<DraftSplit> {
        val scaledAmounts = rescaleSplitMinorUnits(
            rawAmounts = splits.map { it.amount },
            fromDecimals = STORAGE_DECIMALS,
            toDecimals = fromDecimals,
            targetTotal = signedTotalMinor,
        )
        return splits.mapIndexed { idx, split ->
            DraftSplit(
                categoryId = split.categoryId,
                amountMinor = scaledAmounts[idx],
                note = split.note,
            )
        }
    }

    private fun openUriStream(uri: Uri): InputStream? {
        val contentResolverStream = try {
            db.context?.contentResolver?.openInputStream(uri)
        } catch (e: IOException) {
            Timber.d(e, "ContentResolver could not open URI: %s", uri)
            null
        } catch (e: SecurityException) {
            Timber.w(e, "No permission to open URI via ContentResolver: %s", uri)
            null
        } catch (e: UnsupportedOperationException) {
            Timber.d(e, "ContentResolver unsupported for URI: %s", uri)
            null
        } catch (e: IllegalArgumentException) {
            Timber.d(e, "ContentResolver rejected URI: %s", uri)
            null
        }
        val path = uri.path
        return contentResolverStream ?: if (
            uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) && !path.isNullOrBlank()
        ) {
            try {
                File(path).inputStream()
            } catch (e: IOException) {
                Timber.w(e, "Failed to open file stream for path: %s", path)
                null
            }
        } else {
            null
        }
    }

    private fun deletePartialReceipt(file: File?) {
        if (file == null) return
        try {
            if (file.exists() && !file.delete()) {
                Timber.w("Failed to clean up partial receipt file: %s", file.absolutePath)
            }
        } catch (e: SecurityException) {
            Timber.w(e, "No permission to clean up partial receipt file: %s", file.absolutePath)
        }
    }

    internal companion object {
        private const val DEFAULT_DECIMALS = 2
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
                    try {
                        LocalDateTime.parse(trimmed, formatter)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }
}
