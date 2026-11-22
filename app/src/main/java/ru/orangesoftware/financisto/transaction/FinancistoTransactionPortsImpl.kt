// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PicturesUtil
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

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
            is24HourFormat = DateUtils.is24HourFormat(context),
            lastAccountId = MyPreferences.getLastAccount(context),
        )
    }
}

@Serializable
private data class SavedDraftEntry(
    val draftId: String,
    val transactionId: Long,
)

private object DraftSessionCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeDraft(draft: TransactionDraft): String = json.encodeToString(draft)

    fun decodeDraft(raw: String): TransactionDraft? {
        return try {
            json.decodeFromString<TransactionDraft>(raw)
        } catch (e: SerializationException) {
            Timber.w(e, "Failed to decode pending AI transaction draft")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Invalid pending AI transaction draft payload")
            null
        }
    }

    fun encodeHistory(entries: List<SavedDraftEntry>): String = json.encodeToString(entries)

    fun decodeHistory(raw: String): List<SavedDraftEntry> {
        return try {
            json.decodeFromString<List<SavedDraftEntry>>(raw)
        } catch (e: SerializationException) {
            Timber.w(e, "Failed to decode saved AI draft history")
            emptyList()
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Invalid saved AI draft history payload")
            emptyList()
        }
    }
}

class FinancistoDraftSessionStore(
    context: Context,
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DraftSessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override suspend fun getSavedTransactionId(draftId: String): Long? = withContext(ioDispatcher) {
        synchronized(lock) {
            val raw = prefs.getString(KEY_SAVED_DRAFTS, null) ?: return@synchronized null
            DraftSessionCodec.decodeHistory(raw)
                .lastOrNull { it.draftId == draftId }
                ?.transactionId
                ?.takeIf { it > 0L }
        }
    }

    override suspend fun markDraftSaved(draftId: String, transactionId: Long) {
        withContext(ioDispatcher) {
            synchronized(lock) {
                if (transactionId > 0L) {
                    val existing = prefs.getString(KEY_SAVED_DRAFTS, null)
                        ?.let(DraftSessionCodec::decodeHistory)
                        .orEmpty()
                    val retained = existing.filterNot { it.draftId == draftId }
                    val updated = (retained + SavedDraftEntry(draftId = draftId, transactionId = transactionId))
                        .takeLast(MAX_SAVED_DRAFTS)
                    prefs.edit { putString(KEY_SAVED_DRAFTS, DraftSessionCodec.encodeHistory(updated)) }
                }
                removePendingIfMatchingLocked(draftId)
            }
        }
    }

    override suspend fun acknowledgeDraftOpened(draftId: String) {
        withContext(ioDispatcher) {
            synchronized(lock) { removePendingIfMatchingLocked(draftId) }
        }
    }

    override suspend fun clearPendingDraft(draftId: String) {
        withContext(ioDispatcher) {
            synchronized(lock) { removePendingIfMatchingLocked(draftId) }
        }
    }

    fun savePendingDraft(draft: TransactionDraft) {
        synchronized(lock) {
            prefs.edit(commit = true) { putString(KEY_PENDING_DRAFT, DraftSessionCodec.encodeDraft(draft)) }
        }
    }

    fun peekPendingAutoOpenDraft(currentDraftId: String? = null): TransactionDraft? =
        synchronized(lock) { resolvePendingDraftLocked(currentDraftId, removeOnMatch = false) }

    fun consumePendingAutoOpenDraft(currentDraftId: String? = null): TransactionDraft? =
        synchronized(lock) { resolvePendingDraftLocked(currentDraftId, removeOnMatch = true) }

    fun clearPendingAutoOpenDraft(draftId: String) {
        synchronized(lock) { removePendingIfMatchingLocked(draftId) }
    }

    private fun resolvePendingDraftLocked(currentDraftId: String?, removeOnMatch: Boolean): TransactionDraft? {
        val raw = prefs.getString(KEY_PENDING_DRAFT, null) ?: return null
        val draft = DraftSessionCodec.decodeDraft(raw)
        val savedHistory = prefs.getString(KEY_SAVED_DRAFTS, null)
            ?.let(DraftSessionCodec::decodeHistory)
            .orEmpty()
        val alreadySaved = draft != null && savedHistory.any { it.draftId == draft.draftId && it.transactionId > 0L }
        val isCurrentDraft = currentDraftId != null && draft?.draftId == currentDraftId
        val shouldDrop = draft == null || alreadySaved || isCurrentDraft
        if (shouldDrop || removeOnMatch) {
            prefs.edit { remove(KEY_PENDING_DRAFT) }
        }
        return if (shouldDrop) null else draft
    }

    private fun removePendingIfMatchingLocked(draftId: String) {
        val raw = prefs.getString(KEY_PENDING_DRAFT, null) ?: return
        val pending = DraftSessionCodec.decodeDraft(raw)
        if (pending == null || pending.draftId == draftId) {
            prefs.edit { remove(KEY_PENDING_DRAFT) }
        }
    }

    internal companion object {
        const val PREFS_NAME = "financisto_ai_draft_sessions"
        const val KEY_PENDING_DRAFT = "pending_auto_open_draft"
        const val KEY_SAVED_DRAFTS = "saved_draft_history"
        const val MAX_SAVED_DRAFTS = 50
    }
}

class FinancistoExchangeRatePort(
    private val context: Context,
    private val db: DatabaseAdapter,
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExchangeRatePort {
    override suspend fun fetchLiveRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> =
        withContext(ioDispatcher) {
            try {
                val fromCur = CurrencyCache.getCurrency(db, fromCurrencyId)
                val toCur = CurrencyCache.getCurrency(db, toCurrencyId)
                when {
                    fromCur == null -> Result.failure(IllegalArgumentException("Source currency not found"))
                    toCur == null -> Result.failure(IllegalArgumentException("Target currency not found"))
                    else -> {
                        val provider = MyPreferences.createExchangeRatesProvider(context)
                        val rate = provider.getRate(fromCur, toCur)
                        if (rate.isOk && rate.rate > 0.0) {
                            Result.success(rate.rate)
                        } else {
                            val msg = rate.errorMessage?.ifBlank { null } ?: "Unable to fetch rate"
                            Timber.w("Exchange rate fetch failed (%s -> %s): %s", fromCur.name, toCur.name, msg)
                            Result.failure(IllegalStateException(msg))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Failed to fetch live exchange rate (%d -> %d)", fromCurrencyId, toCurrencyId)
                Result.failure(e)
            }
        }
}

class FinancistoReceiptMediaPort(
    private val context: Context,
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReceiptMediaPort {
    override suspend fun stageFromUri(uriString: String): String? = withContext(ioDispatcher) {
        var targetFile: File? = null
        try {
            val uri = uriString.toUri()
            val inputStream = openReceiptStream(uri)
            inputStream?.use { input ->
                coroutineContext.ensureActive()
                val createdFile = PicturesUtil.createEmptyImageFile()
                targetFile = createdFile
                createdFile.outputStream().use { output ->
                    copyWithCancellationChecks(input, output, coroutineContext)
                }
                coroutineContext.ensureActive()
                createdFile.name
            }
        } catch (e: CancellationException) {
            deleteFileQuietly(targetFile)
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            deleteFileQuietly(targetFile)
            Timber.w(e, "Failed to stage receipt image from URI: %s", uriString)
            null
        }
    }

    override fun resolveFilePath(fileName: String): String? {
        val file = PicturesUtil.pictureFile(fileName, true)
        return if (file.exists()) file.absolutePath else null
    }

    override suspend fun deleteStagedFile(fileName: String) {
        withContext(ioDispatcher) {
            try {
                val file = PicturesUtil.pictureFile(fileName, false)
                if (file.exists() && !file.delete()) {
                    Timber.w("Failed to delete staged receipt file: %s", file.absolutePath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Error deleting staged receipt file: %s", fileName)
            }
        }
    }

    private fun openReceiptStream(uri: Uri): InputStream? {
        val contentStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: IOException) {
            Timber.d(e, "ContentResolver could not open receipt URI: %s", uri)
            null
        } catch (e: SecurityException) {
            Timber.w(e, "No permission to open receipt URI: %s", uri)
            null
        } catch (e: UnsupportedOperationException) {
            Timber.d(e, "ContentResolver unsupported for receipt URI: %s", uri)
            null
        }
        return contentStream ?: uri.path?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).inputStream()
        }
    }

    private fun copyWithCancellationChecks(
        input: InputStream,
        output: OutputStream,
        coroutineContext: CoroutineContext,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            output.write(buffer, 0, bytesRead)
        }
        coroutineContext.ensureActive()
    }

    private fun deleteFileQuietly(file: File?) {
        if (file == null) return
        try {
            if (file.exists() && !file.delete()) {
                Timber.w("Failed to clean up partial receipt file: %s", file.absolutePath)
            }
        } catch (e: SecurityException) {
            Timber.w(e, "No permission to clean up partial receipt file: %s", file.absolutePath)
        }
    }
}
