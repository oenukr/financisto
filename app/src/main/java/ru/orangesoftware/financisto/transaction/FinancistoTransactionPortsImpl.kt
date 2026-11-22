// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.DraftSplit
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.File
import java.io.FileInputStream

class FinancistoTransactionPreferencesPort(
    private val context: Context,
) : TransactionPreferencesPort {
    override fun readFormPreferences(): TransactionFormPreferences {
        val rememberAccount = MyPreferences.isRememberAccount(context)
        val rememberCategory = rememberAccount && MyPreferences.isRememberCategory(context)
        return TransactionFormPreferences(
            isRememberAccount = rememberAccount,
            isRememberCategory = rememberCategory,
            isRememberLocation = rememberCategory && MyPreferences.isRememberLocation(context),
            isRememberProject = rememberCategory && MyPreferences.isRememberProject(context),
            isShowPayee = MyPreferences.isShowPayee(context),
            isShowPayeeInTransfers = MyPreferences.isShowPayeeInTransfers(context),
            isShowCurrency = MyPreferences.isShowCurrency(context),
            isShowNote = MyPreferences.isShowNote(context),
            isShowTakePicture = MyPreferences.isShowTakePicture(context),
            isShowIsCCardPayment = MyPreferences.isShowIsCCardPayment(context),
            isSetFocusOnAmountField = MyPreferences.isSetFocusOnAmountField(context),
            isPinProtected = MyPreferences.isPinProtected(context),
            isPinProtectedNewTransaction = MyPreferences.isPinProtectedNewTransaction(context),
            is24HourFormat = DateUtils.is24HourFormat(context),
            lastAccountId = MyPreferences.getLastAccount(context),
        )
    }
}

class FinancistoDraftSessionStore(
    context: Context,
) : DraftSessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getSavedTransactionId(draftId: String): Long? {
        val value = prefs.getLong(draftId, -1L)
        return value.takeIf { it > 0L }
    }

    override suspend fun markDraftSaved(draftId: String, transactionId: Long) {
        if (transactionId > 0L) {
            prefs.edit().putLong(draftId, transactionId).apply()
        }
        removePendingIfMatching(draftId)
    }

    override suspend fun acknowledgeDraftOpened(draftId: String) {
        removePendingIfMatching(draftId)
    }

    override suspend fun clearPendingDraft(draftId: String) {
        removePendingIfMatching(draftId)
    }

    fun savePendingDraft(draft: TransactionDraft) {
        prefs.edit().putString(KEY_PENDING_DRAFT, encodeDraft(draft).toString()).commit()
    }

    fun consumePendingAutoOpenDraft(currentDraftId: String? = null): TransactionDraft? {
        val raw = prefs.getString(KEY_PENDING_DRAFT, null) ?: return null
        prefs.edit().remove(KEY_PENDING_DRAFT).apply()
        val draft = runCatching { decodeDraft(JSONObject(raw)) }.getOrNull() ?: return null
        val alreadySaved = prefs.getLong(draft.draftId, -1L) > 0L
        return if (alreadySaved || (currentDraftId != null && draft.draftId == currentDraftId)) {
            null
        } else {
            draft
        }
    }

    private fun removePendingIfMatching(draftId: String) {
        val raw = prefs.getString(KEY_PENDING_DRAFT, null) ?: return
        val pendingId = runCatching { JSONObject(raw).optString("draftId") }.getOrNull()
        if (pendingId == draftId) {
            prefs.edit().remove(KEY_PENDING_DRAFT).apply()
        }
    }

    private fun encodeDraft(draft: TransactionDraft): JSONObject {
        val splitsJson = JSONArray().apply {
            draft.splits.forEach { split ->
                put(
                    JSONObject()
                        .put("categoryId", split.categoryId)
                        .put("categoryName", split.categoryName)
                        .put("amountMinor", split.amountMinor)
                        .put("note", split.note)
                        .put("projectId", split.projectId)
                )
            }
        }
        return JSONObject()
            .put("draftId", draft.draftId)
            .put("mode", draft.mode.name)
            .put("amountMinor", draft.amountMinor)
            .put("fromAccountId", draft.fromAccountId)
            .put("toAccountId", draft.toAccountId)
            .put("originalCurrencyId", draft.originalCurrencyId)
            .put("originalAmountMinor", draft.originalAmountMinor)
            .put("toAmountMinor", draft.toAmountMinor)
            .put("categoryId", draft.categoryId)
            .put("payeeId", draft.payeeId)
            .put("pendingPayeeName", draft.pendingPayeeName)
            .put("projectId", draft.projectId)
            .put("locationId", draft.locationId)
            .put("dateTimeMillis", draft.dateTimeMillis)
            .put("status", draft.status.name)
            .put("note", draft.note)
            .put("isCCardPayment", draft.isCCardPayment)
            .put("stagedReceiptFileName", draft.stagedReceiptFileName)
            .put("splits", splitsJson)
    }

    private fun decodeDraft(json: JSONObject): TransactionDraft {
        val splitsArray = json.optJSONArray("splits") ?: JSONArray()
        val splits = List(splitsArray.length()) { index ->
            val obj = splitsArray.getJSONObject(index)
            DraftSplit(
                categoryId = obj.getLong("categoryId"),
                categoryName = obj.optStringOrNull("categoryName"),
                amountMinor = obj.getLong("amountMinor"),
                note = obj.optString("note", ""),
                projectId = obj.optLong("projectId", 0L),
            )
        }
        val modeName = json.optString("mode", TransactionMode.EXPENSE.name)
        val statusName = json.optString("status", TransactionStatusOption.UR.name)
        return TransactionDraft(
            draftId = json.getString("draftId"),
            mode = TransactionMode.entries.firstOrNull { it.name == modeName } ?: TransactionMode.EXPENSE,
            amountMinor = json.optLongOrNull("amountMinor"),
            fromAccountId = json.optLongOrNull("fromAccountId"),
            toAccountId = json.optLongOrNull("toAccountId"),
            originalCurrencyId = json.optLongOrNull("originalCurrencyId"),
            originalAmountMinor = json.optLongOrNull("originalAmountMinor"),
            toAmountMinor = json.optLongOrNull("toAmountMinor"),
            categoryId = json.optLongOrNull("categoryId"),
            payeeId = json.optLongOrNull("payeeId"),
            pendingPayeeName = json.optStringOrNull("pendingPayeeName"),
            projectId = json.optLong("projectId", 0L),
            locationId = json.optLong("locationId", 0L),
            dateTimeMillis = json.getLong("dateTimeMillis"),
            status = TransactionStatusOption.entries.firstOrNull { it.name == statusName }
                ?: TransactionStatusOption.UR,
            note = json.optString("note", ""),
            isCCardPayment = json.optBoolean("isCCardPayment", false),
            stagedReceiptFileName = json.optStringOrNull("stagedReceiptFileName"),
            splits = splits,
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val PREFS_NAME = "financisto_ai_draft_sessions"
        const val KEY_PENDING_DRAFT = "pending_auto_open_draft"
    }
}

class FinancistoExchangeRatePort(
    private val context: Context,
    private val db: DatabaseAdapter,
) : ExchangeRatePort {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> {
        return try {
            val fromCur = CurrencyCache.getCurrency(db, fromCurrencyId)
                ?: return Result.failure(IllegalArgumentException("Source currency not found"))
            val toCur = CurrencyCache.getCurrency(db, toCurrencyId)
                ?: return Result.failure(IllegalArgumentException("Target currency not found"))
            val provider = MyPreferences.createExchangeRatesProvider(context)
            val rate = provider.getRate(fromCur, toCur)
            if (rate.isOk && rate.rate > 0.0) {
                Result.success(rate.rate)
            } else {
                Result.failure(IllegalStateException(rate.errorMessage?.ifBlank { null } ?: "Unable to fetch rate"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class FinancistoReceiptMediaPort(
    private val context: Context,
) : ReceiptMediaPort {
    override suspend fun stageFromUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        val inputStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: uri.path?.takeIf { it.isNotBlank() }?.let { runCatching { FileInputStream(File(it)) }.getOrNull() }
            ?: return null
        return runCatching {
            inputStream.use { input ->
                val targetFile = PicturesUtil.createEmptyImageFile()
                targetFile.outputStream().use { output -> input.copyTo(output) }
                targetFile.name
            }
        }.getOrNull()
    }

    override fun resolveFilePath(fileName: String): String? {
        val file = PicturesUtil.pictureFile(fileName, true)
        return if (file.exists()) file.absolutePath else null
    }

    override suspend fun deleteStagedFile(fileName: String) {
        runCatching {
            val file = PicturesUtil.pictureFile(fileName, false)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
