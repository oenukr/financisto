// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.preference.PreferenceManager
import androidx.appstate.datastore.syncToDataStore
import androidx.appstate.statestore.StateStore
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import ru.orangesoftware.financisto.appstate.AiDraftSessionKey
import ru.orangesoftware.financisto.appstate.AiDraftSessionState
import ru.orangesoftware.financisto.appstate.SavedDraftEntry
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.CategoryTree
import ru.orangesoftware.financisto.model.MyLocation
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class FinancistoTransactionPortsTest : AbstractDbTest() {

    private fun clearLegacyDraftPrefs() {
        context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
    }

    private fun sampleDraft(
        draftId: String,
        amountMinor: Long = 1200L,
        stagedReceiptFileName: String? = null,
    ): TransactionDraft = TransactionDraft(
        draftId = draftId,
        mode = TransactionMode.EXPENSE,
        amountMinor = amountMinor,
        fromAccountId = 1L,
        dateTimeMillis = 1_700_000_000_000L,
        stagedReceiptFileName = stagedReceiptFileName,
    )

    @Test
    fun markDraftSavedUpdatesStateStoreAtomicallyDeduplicatesAndCapsHistory() = runTest {
        clearLegacyDraftPrefs()
        val stateStore = StateStore()
        val store = FinancistoDraftSessionStore(context, stateStore, UnconfinedTestDispatcher())
        val totalDrafts = FinancistoDraftSessionStore.MAX_SAVED_DRAFTS + 5

        for (index in 1..totalDrafts) {
            store.markDraftSaved("draft-$index", index.toLong() * 10L)
        }
        // Re-saving an older draft updates its transactionId and moves it to the end without duplicates.
        store.markDraftSaved("draft-6", 999L)

        val pendingDraft = sampleDraft("draft-pending")
        assertTrue(store.savePendingDraft(pendingDraft))
        assertEquals(pendingDraft, store.sessionState.value.pendingDraft)

        // Saving an unrelated draft preserves pendingDraft; saving matching draft clears it atomically.
        store.markDraftSaved("draft-unrelated", 555L)
        assertEquals(pendingDraft, store.peekPendingAutoOpenDraft())
        store.markDraftSaved("draft-pending", 777L)
        assertNull(store.sessionState.value.pendingDraft)
        assertEquals(777L, store.getSavedTransactionId("draft-pending"))

        val history = store.sessionState.value.savedHistory
        assertEquals(FinancistoDraftSessionStore.MAX_SAVED_DRAFTS, history.size)
        assertEquals(1, history.count { it.draftId == "draft-6" })
        assertEquals(999L, store.getSavedTransactionId("draft-6"))
        assertNull(store.getSavedTransactionId("draft-1"))
    }

    @Test
    fun draftSessionStoreMigratesLegacySharedPreferencesOnceAndClearsThem() = runTest {
        val rawPrefs = context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
        val pendingJson =
            """{"draftId":"legacy-pending","mode":"EXPENSE","amountMinor":2500,""" +
                """"fromAccountId":1,"dateTimeMillis":1700000000000}"""
        val historyJson =
            """[{"draftId":"legacy-1","transactionId":11},{"draftId":"legacy-1","transactionId":22}]"""
        rawPrefs.edit(commit = true) {
            clear()
            putString(FinancistoDraftSessionStore.KEY_PENDING_DRAFT, pendingJson)
            putString(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS, historyJson)
        }

        val stateStore = StateStore()
        val store = FinancistoDraftSessionStore(context, stateStore, UnconfinedTestDispatcher())
        assertEquals(22L, store.getSavedTransactionId("legacy-1"))
        assertEquals("legacy-pending", store.peekPendingAutoOpenDraft()?.draftId)
        assertTrue("Legacy SharedPreferences should be cleared after migration", rawPrefs.all.isEmpty())

        // Even if legacy prefs are repopulated later, migration does not re-run.
        rawPrefs.edit(commit = true) {
            putString(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS, """[{"draftId":"stale","transactionId":33}]""")
        }
        assertNull(store.getSavedTransactionId("stale"))
        clearLegacyDraftPrefs()
    }

    @Suppress("InjectDispatcher")
    @Test
    fun draftSessionStorePersistsRoundTripThroughSyncToDataStore() = runBlocking {
        clearLegacyDraftPrefs()
        val tempDir = Files.createTempDirectory("appstate-sync-test").toFile()
        val dataStoreFile = File(tempDir, "financisto_app_state.json")
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val stateStore1 = StateStore()
            val draftStore1 = FinancistoDraftSessionStore(context, stateStore1, Dispatchers.IO)
            val syncJob1 = scope1.launch {
                stateStore1.syncToDataStore(path = dataStoreFile.absolutePath, coroutineScope = scope1)
            }

            val pendingDraft = sampleDraft("draft-roundtrip-pending", amountMinor = 4250L)
            draftStore1.markDraftSaved("draft-roundtrip-saved", 321L)
            draftStore1.savePendingDraft(pendingDraft)
            Snapshot.sendApplyNotifications()

            withTimeout(10_000L) {
                while (!dataStoreFile.exists() || !dataStoreFile.readText().contains("draft-roundtrip-pending")) {
                    delay(25L)
                }
            }
            syncJob1.cancel()
            scope1.cancel()
            scope1.coroutineContext[Job]?.join()

            val stateStore2 = StateStore()
            val draftStore2 = FinancistoDraftSessionStore(context, stateStore2, Dispatchers.IO)
            val syncJob2 = scope2.launch {
                stateStore2.syncToDataStore(path = dataStoreFile.absolutePath, coroutineScope = scope2)
            }

            val expectedState = AiDraftSessionState(
                pendingDraft = pendingDraft,
                savedHistory = listOf(SavedDraftEntry("draft-roundtrip-saved", 321L)),
            )
            withTimeout(10_000L) {
                while (stateStore2.getState(AiDraftSessionKey).value != expectedState) {
                    delay(25L)
                }
            }

            assertEquals(321L, draftStore2.getSavedTransactionId("draft-roundtrip-saved"))
            assertEquals(pendingDraft, draftStore2.peekPendingAutoOpenDraft())
            syncJob2.cancel()
        } finally {
            scope1.cancel()
            scope2.cancel()
            scope2.coroutineContext[Job]?.join()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun savePendingDraftDeletesOverwrittenUnopenedDraftReceiptOnlyWhenOrphaned() = runTest {
        clearLegacyDraftPrefs()
        val store = FinancistoDraftSessionStore(context, StateStore(), UnconfinedTestDispatcher())
        val orphanFileName = "ports_test_orphan_1.jpg"
        val savedFileName = "ports_test_saved_2.jpg"
        val sharedFileName = "ports_test_shared_3.jpg"
        val orphanFile = PicturesUtil.pictureFile(orphanFileName, false).apply { writeBytes(byteArrayOf(1)) }
        val savedFile = PicturesUtil.pictureFile(savedFileName, false).apply { writeBytes(byteArrayOf(2)) }
        val sharedFile = PicturesUtil.pictureFile(sharedFileName, false).apply { writeBytes(byteArrayOf(3)) }
        try {
            val draft1 = sampleDraft("draft-unopened-1", amountMinor = 1000L, stagedReceiptFileName = orphanFileName)
            val draft2 = draft1.copy(draftId = "draft-saved-2", stagedReceiptFileName = savedFileName)
            assertTrue(store.savePendingDraft(draft1))
            assertTrue(store.savePendingDraft(draft2))
            assertFalse("Overwritten unopened draft's staged receipt should be deleted", orphanFile.exists())
            assertTrue(savedFile.exists())

            store.markDraftSaved(draft2.draftId, 77L)
            assertTrue(store.savePendingDraft(draft2))
            val draft3 = draft1.copy(draftId = "draft-shared-3", stagedReceiptFileName = sharedFileName)
            assertTrue(store.savePendingDraft(draft3))
            assertTrue("Already-saved overwritten draft's receipt must be preserved", savedFile.exists())

            val draft4 = draft1.copy(draftId = "draft-shared-4", stagedReceiptFileName = sharedFileName)
            assertTrue(store.savePendingDraft(draft4))
            assertTrue("Receipt reused by replacing draft must be preserved", sharedFile.exists())
        } finally {
            orphanFile.delete()
            savedFile.delete()
            sharedFile.delete()
        }
    }

    @Test
    fun queryRecentNotesAndLoadCatalogReturnEmptyNotesWhenQueryOrCursorFails() = runTest {
        val failingQueryDb = mock(DatabaseAdapter::class.java)
        val failingSqlDb = mock(SQLiteDatabase::class.java)
        `when`(failingQueryDb.allAccountsList).thenReturn(emptyList())
        `when`(failingQueryDb.allCurrenciesList).thenReturn(emptyList())
        `when`(failingQueryDb.getCategoriesTree(false)).thenReturn(CategoryTree())
        `when`(failingQueryDb.allPayeeList).thenReturn(emptyList())
        `when`(failingQueryDb.getAllProjectsList(false)).thenReturn(ArrayList<Project>())
        `when`(failingQueryDb.getAllLocationsList(false)).thenReturn(ArrayList<MyLocation>())
        `when`(failingQueryDb.db()).thenReturn(failingSqlDb)
        `when`(failingSqlDb.rawQuery(anyString(), any())).thenThrow(SQLiteException("Simulated notes query failure"))

        val repo = FinancistoTransactionRepository(context, failingQueryDb, UnconfinedTestDispatcher())
        val catalog = repo.loadCatalog(editingTransactionId = null)
        assertTrue(catalog.recentNotes.isEmpty())

        val failingCursorDb = mock(DatabaseAdapter::class.java)
        val cursorSqlDb = mock(SQLiteDatabase::class.java)
        val failingCursor = mock(Cursor::class.java)
        `when`(failingCursorDb.db()).thenReturn(cursorSqlDb)
        `when`(cursorSqlDb.rawQuery(anyString(), any())).thenReturn(failingCursor)
        `when`(failingCursor.moveToNext()).thenThrow(SQLiteException("Simulated cursor failure"))

        assertEquals(emptyList<String>(), FinancistoTransactionMapper.queryRecentNotes(failingCursorDb))
        verify(failingCursor).close()
    }

    @Test
    fun exchangeRatePortCatchesInvalidProviderPreferenceAndReturnsFailure() = runTest {
        val usd = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").create()
        val eur = CurrencyBuilder.withDb(db).name("EUR").title("Euro").symbol("€").create()
        CurrencyCache.initialize(db)

        @Suppress("DEPRECATION")
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = defaultPrefs.getString("exchange_rate_provider", null)
        defaultPrefs.edit(commit = true) { putString("exchange_rate_provider", "unknown_provider") }
        try {
            val port = FinancistoExchangeRatePort(context, db, UnconfinedTestDispatcher())
            val result = port.fetchLiveRate(usd.id, eur.id)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        } finally {
            defaultPrefs.edit(commit = true) {
                if (previous == null) {
                    remove("exchange_rate_provider")
                } else {
                    putString("exchange_rate_provider", previous)
                }
            }
        }
    }

    @Test
    fun legacyMigrationKeepsPrefsIntactOnPersistFailureAndRetriesOnNextCall() = runTest {
        clearLegacyDraftPrefs()
        val legacyPrefs = context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val legacyDraft = sampleDraft("draft-legacy-retry", amountMinor = 1900L)
        legacyPrefs.edit(commit = true) {
            putString(FinancistoDraftSessionStore.KEY_PENDING_DRAFT, json.encodeToString(legacyDraft))
        }

        var shouldFail = true
        val persistedStates = mutableListOf<AiDraftSessionState>()
        val store = FinancistoDraftSessionStore(
            context = context,
            stateStore = StateStore(),
            ioDispatcher = UnconfinedTestDispatcher(),
            persistSessionState = { state ->
                if (shouldFail) {
                    throw IOException("Simulated DataStore write failure")
                }
                persistedStates += state
            },
        )

        assertNull(store.peekPendingAutoOpenDraft())
        assertTrue(legacyPrefs.contains(FinancistoDraftSessionStore.KEY_PENDING_DRAFT))

        shouldFail = false
        val migrated = store.peekPendingAutoOpenDraft()
        assertEquals(legacyDraft, migrated)
        assertFalse(legacyPrefs.contains(FinancistoDraftSessionStore.KEY_PENDING_DRAFT))
        assertEquals(1, persistedStates.size)
        assertEquals(legacyDraft, persistedStates.single().pendingDraft)
    }

    @Test
    fun savePendingDraftRetainsPreviousDraftAndOrphanReceiptWhenPersistFails() = runTest {
        clearLegacyDraftPrefs()
        var shouldFail = false
        val store = FinancistoDraftSessionStore(
            context = context,
            stateStore = StateStore(),
            ioDispatcher = UnconfinedTestDispatcher(),
            persistSessionState = {
                if (shouldFail) {
                    throw IOException("Simulated write failure")
                }
            },
        )
        val firstReceiptName = "ports_test_keep_on_fail_1.jpg"
        val replacementReceiptName = "ports_test_keep_on_fail_2.jpg"
        val firstReceipt = PicturesUtil.pictureFile(firstReceiptName, false).apply { writeBytes(byteArrayOf(1)) }
        val replacementReceipt = PicturesUtil.pictureFile(replacementReceiptName, false).apply {
            writeBytes(byteArrayOf(2))
        }
        try {
            val firstDraft = sampleDraft("draft-keep-1", amountMinor = 1100L, stagedReceiptFileName = firstReceiptName)
            val replacementDraft = sampleDraft(
                "draft-replace-2",
                amountMinor = 2200L,
                stagedReceiptFileName = replacementReceiptName,
            )
            assertTrue(store.savePendingDraft(firstDraft))

            shouldFail = true
            assertFalse(store.savePendingDraft(replacementDraft))
            assertEquals(firstDraft, store.peekPendingAutoOpenDraft())
            assertTrue("Previous receipt must not be deleted if replacement fails to persist", firstReceipt.exists())
        } finally {
            firstReceipt.delete()
            replacementReceipt.delete()
        }
    }

    @Test
    fun legacySavedHistoryMigrationFailurePropagatesAndRetriesWithoutReturningDefaultNull() = runTest {
        clearLegacyDraftPrefs()
        val legacyPrefs = context.getSharedPreferences(FinancistoDraftSessionStore.PREFS_NAME, Context.MODE_PRIVATE)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val legacyHistory = listOf(SavedDraftEntry(draftId = "legacy-tx-1", transactionId = 42L))
        legacyPrefs.edit(commit = true) {
            putString(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS, json.encodeToString(legacyHistory))
        }

        var shouldFail = true
        val persistedStates = mutableListOf<AiDraftSessionState>()
        val store = FinancistoDraftSessionStore(
            context = context,
            stateStore = StateStore(),
            ioDispatcher = UnconfinedTestDispatcher(),
            persistSessionState = { state ->
                if (shouldFail) {
                    throw IOException("Simulated DataStore write failure")
                }
                persistedStates += state
            },
        )

        val failure = runCatching { store.getSavedTransactionId("legacy-tx-1") }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(legacyPrefs.contains(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS))

        shouldFail = false
        assertEquals(42L, store.getSavedTransactionId("legacy-tx-1"))
        assertFalse(legacyPrefs.contains(FinancistoDraftSessionStore.KEY_SAVED_DRAFTS))
        assertEquals(1, persistedStates.size)
        assertEquals(legacyHistory, persistedStates.single().savedHistory)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun getSavedTransactionIdWaitsForInitialAppStateRestoreBeforeReadingHistory() = runTest {
        clearLegacyDraftPrefs()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateStore = StateStore()
        val restoreGate = CompletableDeferred<Unit>()
        val store = FinancistoDraftSessionStore(
            context = context,
            stateStore = stateStore,
            ioDispatcher = dispatcher,
            awaitSessionRestore = {
                restoreGate.await()
                Snapshot.withMutableSnapshot {
                    stateStore.setState(
                        AiDraftSessionKey,
                        AiDraftSessionState(
                            savedHistory = listOf(SavedDraftEntry("draft-gated", 888L)),
                        ),
                    )
                }
            },
        )

        val lookup = async { store.getSavedTransactionId("draft-gated") }
        runCurrent()
        assertFalse(lookup.isCompleted)

        restoreGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(888L, lookup.await())
    }

    @Test
    fun markDraftSavedPersistsDurablyRetainsBookkeepingOnFailureAndPreventsDuplicateSaveOnRetry() = runTest {
        clearLegacyDraftPrefs()
        var shouldFail = true
        val persistedStates = mutableListOf<AiDraftSessionState>()
        val store = FinancistoDraftSessionStore(
            context = context,
            stateStore = StateStore(),
            ioDispatcher = UnconfinedTestDispatcher(),
            persistSessionState = { state ->
                if (shouldFail) {
                    throw IOException("Simulated DataStore write failure")
                }
                persistedStates += state
            },
        )

        val failure = runCatching { store.markDraftSaved("draft-dup-guard", 702L) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(702L, store.getSavedTransactionId("draft-dup-guard"))

        shouldFail = false
        store.markDraftSaved("draft-dup-guard", 702L)
        assertEquals(702L, store.getSavedTransactionId("draft-dup-guard"))
        assertEquals(
            listOf(SavedDraftEntry("draft-dup-guard", 702L)),
            persistedStates.last().savedHistory,
        )
    }
}
