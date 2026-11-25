// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.preference.PreferenceManager
import androidx.core.content.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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

@OptIn(ExperimentalCoroutinesApi::class)
class FinancistoTransactionPortsTest : AbstractDbTest() {

    @Test
    fun savePendingDraftUsesSynchronousCommitAndReturnsResult() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.commit()).thenReturn(false, true)

        val store = FinancistoDraftSessionStore(mockContext, UnconfinedTestDispatcher())
        val draft = TransactionDraft(
            draftId = "draft-commit-check",
            mode = TransactionMode.EXPENSE,
            amountMinor = 1200L,
            fromAccountId = 1L,
            dateTimeMillis = 1_700_000_000_000L,
        )

        assertFalse(store.savePendingDraft(draft))
        assertTrue(store.savePendingDraft(draft))
        verify(mockEditor, never()).apply()
    }

    @Test
    fun markDraftSavedCommitsHistoryAndClearsMatchingPendingDraftSynchronously() = runTest {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)
        `when`(mockEditor.commit()).thenReturn(false)

        val store = FinancistoDraftSessionStore(mockContext, UnconfinedTestDispatcher())
        store.markDraftSaved("draft-sync-1", 99L)

        verify(mockEditor).putString(anyString(), anyString())
        verify(mockEditor).commit()
        verify(mockEditor, never()).apply()
    }

    @Test
    fun savePendingDraftDeletesOverwrittenUnopenedDraftReceiptOnlyWhenOrphaned() = runTest {
        val store = FinancistoDraftSessionStore(context, UnconfinedTestDispatcher())
        val orphanFileName = "ports_test_orphan_1.jpg"
        val savedFileName = "ports_test_saved_2.jpg"
        val sharedFileName = "ports_test_shared_3.jpg"
        val orphanFile = PicturesUtil.pictureFile(orphanFileName, false).apply { writeBytes(byteArrayOf(1)) }
        val savedFile = PicturesUtil.pictureFile(savedFileName, false).apply { writeBytes(byteArrayOf(2)) }
        val sharedFile = PicturesUtil.pictureFile(sharedFileName, false).apply { writeBytes(byteArrayOf(3)) }
        try {
            val draft1 = TransactionDraft(
                draftId = "draft-unopened-1",
                mode = TransactionMode.EXPENSE,
                amountMinor = 1000L,
                fromAccountId = 1L,
                dateTimeMillis = 1_700_000_000_000L,
                stagedReceiptFileName = orphanFileName,
            )
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
}
