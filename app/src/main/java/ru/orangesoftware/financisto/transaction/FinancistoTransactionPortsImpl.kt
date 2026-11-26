// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.appstate.statestore.StateStore
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONException
import ru.orangesoftware.financisto.appstate.AiDraftSessionKey
import ru.orangesoftware.financisto.appstate.AiDraftSessionState
import ru.orangesoftware.financisto.appstate.SavedDraftEntry
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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

private object DraftSessionCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

    data class SavePendingDraftResult(
        val persisted: Boolean,
        val orphanReceiptFile: String? = null,
    )

    suspend fun migrateFromLegacyIfNeeded(
        legacyPrefs: SharedPreferences?,
        stateStore: StateStore,
        persistSessionState: (suspend (AiDraftSessionState) -> Unit)?,
    ): Boolean {
        val legacyPendingRaw = legacyPrefs?.getString(FinancistoDraftSessionStore.KEY_PENDING_DRAFT, null)
        val legacySavedRaw = legacyPrefs?.getString(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS, null)
        if (legacyPrefs == null || (legacyPendingRaw == null && legacySavedRaw == null)) {
            return true
        }
        val migratedState = buildMigratedStateIfNeeded(
            currentState = stateStore.getState(AiDraftSessionKey).value,
            legacyPendingRaw = legacyPendingRaw,
            legacySavedRaw = legacySavedRaw,
        )
        val persisted = migratedState == null || persistState(persistSessionState, migratedState)
        if (persisted) {
            if (migratedState != null) {
                Snapshot.withMutableSnapshot {
                    stateStore.setState(AiDraftSessionKey, migratedState)
                }
            }
            val cleared = legacyPrefs.edit()?.run {
                clear()
                commit()
            } ?: true
            if (!cleared) {
                Timber.w("Failed to clear legacy AI draft preferences after migration")
            }
        }
        return persisted
    }

    private fun buildMigratedStateIfNeeded(
        currentState: AiDraftSessionState,
        legacyPendingRaw: String?,
        legacySavedRaw: String?,
    ): AiDraftSessionState? {
        val isStoreAtDefault = currentState.pendingDraft == null && currentState.savedHistory.isEmpty()
        if (!isStoreAtDefault) return null
        val legacyPending = legacyPendingRaw?.let(::decodeDraft)
        val deduplicatedHistory = LinkedHashMap<String, SavedDraftEntry>()
        legacySavedRaw?.let(::decodeHistory)
            .orEmpty()
            .asSequence()
            .filter { it.transactionId > 0L }
            .forEach { entry ->
                deduplicatedHistory.remove(entry.draftId)
                deduplicatedHistory[entry.draftId] = entry
            }
        val legacyHistory = deduplicatedHistory.values.toList()
            .takeLast(FinancistoDraftSessionStore.MAX_SAVED_DRAFTS)
        return if (legacyPending != null || legacyHistory.isNotEmpty()) {
            AiDraftSessionState(
                pendingDraft = legacyPending,
                savedHistory = legacyHistory,
            )
        } else {
            null
        }
    }

    suspend fun savePendingDraftLocked(
        stateStore: StateStore,
        pendingBookkeeping: MutableMap<String, Long>,
        draft: TransactionDraft,
        persistSessionState: (suspend (AiDraftSessionState) -> Unit)?,
    ): SavePendingDraftResult {
        val current = stateStore.getState(AiDraftSessionKey).value
        val mergedHistory = DraftBookkeepingHelper.mergeBookkeepingIntoHistory(
            history = current.savedHistory,
            pendingBookkeeping = pendingBookkeeping,
        )
        val previous = current.pendingDraft
        val previousId = previous?.draftId
        val candidateOrphan = previous
            ?.takeIf { it.draftId != draft.draftId }
            ?.stagedReceiptFileName
            ?.takeIf { it.isNotBlank() && it != draft.stagedReceiptFileName }
        val alreadySaved = previousId != null && candidateOrphan != null &&
            mergedHistory.any { it.draftId == previousId && it.transactionId > 0L }
        val orphanReceiptFile = candidateOrphan.takeUnless { alreadySaved }
        val nextState = current.copy(pendingDraft = draft, savedHistory = mergedHistory)
        if (!persistState(persistSessionState, nextState)) {
            return SavePendingDraftResult(persisted = false)
        }
        pendingBookkeeping.clear()
        Snapshot.withMutableSnapshot {
            stateStore.setState(AiDraftSessionKey, nextState)
        }
        return SavePendingDraftResult(
            persisted = true,
            orphanReceiptFile = orphanReceiptFile,
        )
    }

    suspend fun persistState(
        persistSessionState: (suspend (AiDraftSessionState) -> Unit)?,
        state: AiDraftSessionState,
    ): Boolean {
        val writer = persistSessionState ?: return true
        return try {
            writer(state)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist AI draft session state to DataStore")
            false
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to serialize AI draft session state for DataStore")
            false
        }
    }

    fun removePendingIfMatching(stateStore: StateStore, draftId: String) {
        if (stateStore.getState(AiDraftSessionKey).value.pendingDraft?.draftId == draftId) {
            Snapshot.withMutableSnapshot {
                stateStore.updateState(AiDraftSessionKey) { it.copy(pendingDraft = null) }
            }
        }
    }
}

private object DraftBookkeepingHelper {
    fun recordPending(
        pendingBookkeeping: LinkedHashMap<String, Long>,
        draftId: String,
        transactionId: Long,
    ) {
        if (transactionId > 0L) {
            pendingBookkeeping.remove(draftId)
            pendingBookkeeping[draftId] = transactionId
            while (pendingBookkeeping.size > FinancistoDraftSessionStore.MAX_SAVED_DRAFTS) {
                val eldestKey = pendingBookkeeping.entries.firstOrNull()?.key ?: break
                pendingBookkeeping.remove(eldestKey)
            }
        }
    }

    fun mergeBookkeepingIntoHistory(
        history: List<SavedDraftEntry>,
        pendingBookkeeping: Map<String, Long>,
    ): List<SavedDraftEntry> {
        if (pendingBookkeeping.isEmpty()) return history
        val deduplicated = LinkedHashMap<String, Long>()
        history.forEach { entry ->
            if (entry.transactionId > 0L) {
                deduplicated.remove(entry.draftId)
                deduplicated[entry.draftId] = entry.transactionId
            }
        }
        pendingBookkeeping.forEach { (draftId, txId) ->
            if (txId > 0L) {
                deduplicated.remove(draftId)
                deduplicated[draftId] = txId
            }
        }
        return deduplicated.entries
            .map { (draftId, txId) -> SavedDraftEntry(draftId = draftId, transactionId = txId) }
            .takeLast(FinancistoDraftSessionStore.MAX_SAVED_DRAFTS)
    }

    suspend fun awaitRestoreSafely(awaitSessionRestore: (suspend () -> Unit)?): Boolean {
        return awaitSessionRestore?.let { restore ->
            try {
                restore()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Timber.e(e, "Failed to restore AppState AI draft session from DataStore")
                false
            } catch (e: SerializationException) {
                Timber.e(e, "Failed to decode restored AppState AI draft session")
                false
            }
        } ?: true
    }

    suspend fun applyAndPersistSaved(
        stateStore: StateStore,
        pendingBookkeeping: MutableMap<String, Long>,
        draftId: String,
        transactionId: Long,
        persistSessionState: (suspend (AiDraftSessionState) -> Unit)?,
    ): Boolean {
        val current = stateStore.getState(AiDraftSessionKey).value
        val toMerge = if (transactionId > 0L) {
            LinkedHashMap(pendingBookkeeping).apply {
                remove(draftId)
                put(draftId, transactionId)
            }
        } else {
            pendingBookkeeping
        }
        val nextState = current.copy(
            pendingDraft = current.pendingDraft?.takeUnless {
                it.draftId == draftId || it.draftId in pendingBookkeeping
            },
            savedHistory = mergeBookkeepingIntoHistory(current.savedHistory, toMerge),
        )
        Snapshot.withMutableSnapshot {
            stateStore.setState(AiDraftSessionKey, nextState)
        }
        val persisted = DraftSessionCodec.persistState(persistSessionState, nextState)
        if (persisted) {
            pendingBookkeeping.clear()
        }
        return persisted
    }

    suspend fun flushPendingIfAny(
        stateStore: StateStore,
        pendingBookkeeping: MutableMap<String, Long>,
        persistSessionState: (suspend (AiDraftSessionState) -> Unit)?,
    ): Boolean {
        if (pendingBookkeeping.isEmpty()) return true
        val current = stateStore.getState(AiDraftSessionKey).value
        val nextState = current.copy(
            pendingDraft = current.pendingDraft?.takeUnless { it.draftId in pendingBookkeeping },
            savedHistory = mergeBookkeepingIntoHistory(current.savedHistory, pendingBookkeeping),
        )
        Snapshot.withMutableSnapshot {
            stateStore.setState(AiDraftSessionKey, nextState)
        }
        val persisted = DraftSessionCodec.persistState(persistSessionState, nextState)
        if (persisted) {
            pendingBookkeeping.clear()
        }
        return persisted
    }

    suspend fun clearPendingAndFlush(
        stateStore: StateStore,
        pendingBookkeeping: MutableMap<String, Long>,
        draftId: String,
        persistSessionState: (suspend (AiDraftSessionState) -> Unit)?,
    ) {
        val current = stateStore.getState(AiDraftSessionKey).value
        val nextPending = current.pendingDraft?.takeUnless {
            it.draftId == draftId || it.draftId in pendingBookkeeping
        }
        val nextHistory = mergeBookkeepingIntoHistory(current.savedHistory, pendingBookkeeping)
        val nextState = current.copy(pendingDraft = nextPending, savedHistory = nextHistory)
        if (nextState != current || pendingBookkeeping.isNotEmpty()) {
            Snapshot.withMutableSnapshot {
                stateStore.setState(AiDraftSessionKey, nextState)
            }
            if (DraftSessionCodec.persistState(persistSessionState, nextState)) {
                pendingBookkeeping.clear()
            }
        }
    }
}

class FinancistoDraftSessionStore(
    context: Context,
    private val stateStore: StateStore = StateStore(),
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persistSessionState: (suspend (AiDraftSessionState) -> Unit)? = null,
    private val awaitSessionRestore: (suspend () -> Unit)? = null,
) : DraftSessionStore {
    @Suppress("InjectDispatcher")
    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
    ) : this(context = context, stateStore = StateStore(), ioDispatcher = ioDispatcher)

    private val prefs: SharedPreferences? = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val migratedFromLegacy = AtomicBoolean(false)
    private val pendingSavedBookkeeping = LinkedHashMap<String, Long>()

    val sessionState: State<AiDraftSessionState> = stateStore.getState(AiDraftSessionKey)

    @VisibleForTesting
    internal var deleteStagedReceipt: (String) -> Unit = ::deleteStagedReceiptQuietly

    override suspend fun getSavedTransactionId(draftId: String): Long? = withContext(ioDispatcher) {
        mutex.withLock {
            val retainedId = pendingSavedBookkeeping[draftId]?.takeIf { it > 0L }
            val ready = DraftBookkeepingHelper.awaitRestoreSafely(awaitSessionRestore) &&
                ensureMigratedFromLegacyLocked()
            when {
                !ready -> retainedId ?: throw IOException("Failed to initialize AI draft session state")
                else -> {
                    DraftBookkeepingHelper.flushPendingIfAny(
                        stateStore = stateStore,
                        pendingBookkeeping = pendingSavedBookkeeping,
                        persistSessionState = persistSessionState,
                    )
                    retainedId ?: sessionState.value.savedHistory
                        .lastOrNull { it.draftId == draftId }
                        ?.transactionId
                        ?.takeIf { it > 0L }
                }
            }
        }
    }

    override suspend fun markDraftSaved(draftId: String, transactionId: Long) {
        withContext(ioDispatcher) {
            mutex.withLock {
                DraftBookkeepingHelper.recordPending(pendingSavedBookkeeping, draftId, transactionId)
                val ready = DraftBookkeepingHelper.awaitRestoreSafely(awaitSessionRestore) &&
                    ensureMigratedFromLegacyLocked()
                val persisted = ready && DraftBookkeepingHelper.applyAndPersistSaved(
                    stateStore = stateStore,
                    pendingBookkeeping = pendingSavedBookkeeping,
                    draftId = draftId,
                    transactionId = transactionId,
                    persistSessionState = persistSessionState,
                )
                if (!persisted) {
                    throw IOException("Failed to durably persist saved AI draft bookkeeping for $draftId")
                }
            }
        }
    }

    override suspend fun acknowledgeDraftOpened(draftId: String) {
        clearPendingDraft(draftId)
    }

    override suspend fun clearPendingDraft(draftId: String) {
        withContext(ioDispatcher) {
            mutex.withLock {
                val ready = DraftBookkeepingHelper.awaitRestoreSafely(awaitSessionRestore) &&
                    ensureMigratedFromLegacyLocked()
                if (!ready) {
                    throw IOException("Failed to initialize AI draft session state")
                }
                DraftBookkeepingHelper.clearPendingAndFlush(
                    stateStore = stateStore,
                    pendingBookkeeping = pendingSavedBookkeeping,
                    draftId = draftId,
                    persistSessionState = persistSessionState,
                )
            }
        }
    }

    fun savePendingDraft(draft: TransactionDraft): Boolean {
        var orphanReceiptFile: String? = null
        val persisted = runBlocking {
            mutex.withLock {
                val ready = DraftBookkeepingHelper.awaitRestoreSafely(awaitSessionRestore) &&
                    ensureMigratedFromLegacyLocked()
                if (ready) {
                    val result = DraftSessionCodec.savePendingDraftLocked(
                        stateStore = stateStore,
                        pendingBookkeeping = pendingSavedBookkeeping,
                        draft = draft,
                        persistSessionState = persistSessionState,
                    )
                    orphanReceiptFile = result.orphanReceiptFile
                    result.persisted
                } else {
                    false
                }
            }
        }
        if (persisted) {
            orphanReceiptFile?.let(deleteStagedReceipt)
        }
        return persisted
    }

    fun peekPendingAutoOpenDraft(currentDraftId: String? = null): TransactionDraft? =
        runBlocking {
            mutex.withLock {
                if (ensureMigratedFromLegacyLocked()) {
                    resolvePendingDraftLocked(currentDraftId, removeOnMatch = false)
                } else {
                    null
                }
            }
        }

    fun consumePendingAutoOpenDraft(currentDraftId: String? = null): TransactionDraft? =
        runBlocking {
            mutex.withLock {
                if (ensureMigratedFromLegacyLocked()) {
                    resolvePendingDraftLocked(currentDraftId, removeOnMatch = true)
                } else {
                    null
                }
            }
        }

    fun clearPendingAutoOpenDraft(draftId: String) {
        runBlocking {
            mutex.withLock {
                if (ensureMigratedFromLegacyLocked()) {
                    DraftSessionCodec.removePendingIfMatching(stateStore, draftId)
                }
            }
        }
    }

    private suspend fun ensureMigratedFromLegacyLocked(): Boolean {
        if (!migratedFromLegacy.get()) {
            val succeeded = DraftSessionCodec.migrateFromLegacyIfNeeded(
                legacyPrefs = prefs,
                stateStore = stateStore,
                persistSessionState = persistSessionState,
            )
            if (succeeded) {
                migratedFromLegacy.set(true)
            }
        }
        return migratedFromLegacy.get()
    }

    private fun resolvePendingDraftLocked(currentDraftId: String?, removeOnMatch: Boolean): TransactionDraft? {
        val current = sessionState.value
        val draft = current.pendingDraft ?: return null
        val alreadySaved = draft.draftId in pendingSavedBookkeeping ||
            current.savedHistory.any { it.draftId == draft.draftId && it.transactionId > 0L }
        val isCurrentDraft = currentDraftId != null && draft.draftId == currentDraftId
        val shouldDrop = alreadySaved || isCurrentDraft
        if (shouldDrop || removeOnMatch) {
            Snapshot.withMutableSnapshot {
                stateStore.updateState(AiDraftSessionKey) { it.copy(pendingDraft = null) }
            }
        }
        return if (shouldDrop) null else draft
    }

    internal companion object {
        const val PREFS_NAME = "financisto_ai_draft_sessions"
        const val KEY_PENDING_DRAFT = "pending_auto_open_draft"
        const val KEY_SAVED_DRAFTS = "saved_draft_history"
        const val MAX_SAVED_DRAFTS = 50

        private fun deleteStagedReceiptQuietly(fileName: String) {
            try {
                val file = PicturesUtil.pictureFile(fileName, false)
                if (file.exists() && !file.delete()) {
                    Timber.w("Failed to delete orphaned staged receipt file: %s", file.absolutePath)
                }
            } catch (e: SecurityException) {
                Timber.w(e, "No permission to delete orphaned staged receipt file: %s", fileName)
            }
        }
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
                resolveAndFetchRate(fromCurrencyId, toCurrencyId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            } catch (e: JSONException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            } catch (e: SQLiteException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            } catch (e: SecurityException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            } catch (e: IllegalArgumentException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            } catch (e: IllegalStateException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            } catch (e: UnsupportedOperationException) {
                logAndFail(fromCurrencyId, toCurrencyId, e)
            }
        }

    private fun resolveAndFetchRate(fromCurrencyId: Long, toCurrencyId: Long): Result<Double> {
        val fromCur = CurrencyCache.getCurrency(db, fromCurrencyId)
        val toCur = CurrencyCache.getCurrency(db, toCurrencyId)
        return when {
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
    }

    private fun logAndFail(fromCurrencyId: Long, toCurrencyId: Long, error: Exception): Result<Double> {
        Timber.w(error, "Failed to fetch live exchange rate (%d -> %d)", fromCurrencyId, toCurrencyId)
        return Result.failure(error)
    }
}

class FinancistoReceiptMediaPort(
    private val context: Context,
    @Suppress("InjectDispatcher")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReceiptMediaPort {
    override suspend fun stageFromUri(uriString: String): String? {
        val createdFileRef = AtomicReference<File?>(null)
        return try {
            withContext(ioDispatcher) {
                copyReceiptFromUri(uriString, createdFileRef, coroutineContext)
            }
        } catch (e: CancellationException) {
            createdFileRef.getAndSet(null)?.let { leakedFile ->
                withContext(NonCancellable + ioDispatcher) {
                    deleteFileQuietly(leakedFile)
                }
            }
            throw e
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
            } catch (e: SecurityException) {
                Timber.w(e, "No permission to delete staged receipt file: %s", fileName)
            }
        }
    }

    private fun copyReceiptFromUri(
        uriString: String,
        createdFileRef: AtomicReference<File?>,
        coroutineContext: CoroutineContext,
    ): String? {
        var succeeded = false
        return try {
            val uri = uriString.toUri()
            val stagedName = openReceiptStream(uri)?.use { input ->
                coroutineContext.ensureActive()
                val createdFile = PicturesUtil.createEmptyImageFile()
                createdFileRef.set(createdFile)
                createdFile.outputStream().use { output ->
                    copyWithCancellationChecks(input, output, coroutineContext)
                }
                createdFile.name
            }
            if (stagedName != null) {
                coroutineContext.ensureActive()
                succeeded = true
            }
            stagedName
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Failed to stage receipt image from URI: %s", uriString)
            null
        } catch (e: SecurityException) {
            Timber.w(e, "No permission to stage receipt image from URI: %s", uriString)
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Invalid receipt URI: %s", uriString)
            null
        } catch (e: IllegalStateException) {
            Timber.w(e, "Unexpected provider state while staging receipt URI: %s", uriString)
            null
        } catch (e: UnsupportedOperationException) {
            Timber.w(e, "Unsupported receipt URI: %s", uriString)
            null
        } finally {
            if (!succeeded) {
                deleteFileQuietly(createdFileRef.getAndSet(null))
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
        } catch (e: IllegalArgumentException) {
            Timber.d(e, "ContentResolver rejected receipt URI: %s", uri)
            null
        }
        return contentStream ?: if (uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true)) {
            uri.path?.takeIf { it.isNotBlank() }?.let { path -> File(path).inputStream() }
        } else {
            null
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
