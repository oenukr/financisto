// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.preference.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.backup.DatabaseExport
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportTask
import ru.orangesoftware.financisto.export.dropbox.DropboxRestoreTask
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

@Suppress("MagicNumber", "TooManyFunctions", "DEPRECATION")
class ImportExportTasksAdversarialTest : AbstractDbTest() {

    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var progressDialog: ProgressDialog
    private lateinit var defaultCurrency: Currency
    private lateinit var defaultAccount: Account

    @Before
    override fun setUp() {
        super.setUp()
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activity = activityController.get()
        progressDialog = ProgressDialog(activity)

        defaultCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .separators("','", "'.'")
            .makeDefault()
            .create()

        defaultAccount = AccountBuilder.createDefault(db, defaultCurrency)
        CurrencyCache.initialize(db)
    }

    private class AdversarialTestTask(
        context: Activity,
        dialog: ProgressDialog?,
        scope: CoroutineScope? = null,
        private val workBlock: suspend (Context, DatabaseAdapter) -> Any?,
    ) : ImportExportAsyncTask(context, dialog, scope) {

        fun callPublishProgress(value: String) {
            publishProgress(value)
        }

        override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? =
            workBlock(context, db)

        override fun getSuccessMessage(result: Any?): String = "Success: $result"
    }

    // =========================================================================
    // 1. CONCURRENCY AND CANCELLATION TESTS
    // =========================================================================

    @Test
    fun cancel_duringInFlightWork_cancelsJobAndAbortsPostExecute() {
        val workStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        var listenerCalled = false

        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            workStarted.complete(Unit)
            allowFinish.await()
            "CompletedResult"
        }
        task.setListener { listenerCalled = true }

        val job = task.execute()
        runBlocking { workStarted.await() }

        val wasActive = task.cancel()
        assertTrue("Cancel should report true for running job", wasActive)
        assertTrue("Task should report isCancelled", task.isCancelled)
        assertTrue("Job should be cancelled", job.isCancelled)

        allowFinish.complete(Unit)
        runBlocking { job.join() }
        ShadowLooper.idleMainLooper()

        assertFalse("Listener should not be invoked on cancellation", listenerCalled)
        assertNull("Toast should not be shown on cancellation", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun cancel_propagatesCancellationExceptionToGet() {
        val workStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()

        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            workStarted.complete(Unit)
            allowFinish.await()
            "Result"
        }

        val job = task.execute()
        runBlocking { workStarted.await() }

        task.cancel()
        allowFinish.complete(Unit)
        runBlocking { job.join() }

        try {
            task.get()
            fail("Expected CancellationException from task.get() after cancellation")
        } catch (e: CancellationException) {
            assertNotNull(e)
        }
    }

    @Test
    fun workThrowsCancellationException_propagatesCleanlyAndDoesNotCatchAsGenericError() {
        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            throw CancellationException("Simulated explicit cancellation inside work")
        }

        val job = task.execute()
        runBlocking { job.join() }
        ShadowLooper.idleMainLooper()

        assertTrue("Job should be marked cancelled", job.isCancelled)
        assertTrue("Task should report isCancelled", task.isCancelled)
        assertNull(
            "Error dialog should not be shown for CancellationException",
            ShadowAlertDialog.getLatestAlertDialog(),
        )
        assertNull("Toast should not be shown for CancellationException", ShadowToast.getTextOfLatestToast())

        try {
            task.get()
            fail("Expected CancellationException from task.get()")
        } catch (e: CancellationException) {
            assertTrue(e.message?.contains("Simulated explicit cancellation") == true)
        }
    }

    @Test
    fun multipleRapidExecutions_cancelsPreviousJobsAndOnlyLastCompletes() {
        val completionResults = mutableListOf<Any?>()
        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            Thread.sleep(10)
            "Done"
        }
        task.setListener { res ->
            synchronized(completionResults) { completionResults.add(res) }
        }

        val job1 = task.execute("run1")
        val job2 = task.execute("run2")
        val job3 = task.execute("run3")

        val result = task.get()
        Thread.sleep(50)
        ShadowLooper.idleMainLooper()

        assertTrue("Job 1 should be cancelled", job1.isCancelled)
        assertTrue("Job 2 should be cancelled", job2.isCancelled)
        assertFalse("Job 3 should complete successfully", job3.isCancelled)
        assertEquals("Done", result)
        assertEquals(listOf("Done"), completionResults)
    }

    @Test
    fun executeWork_dropboxUploadRunsOutsideDbMutex_doesNotBlockConcurrentDbLock() {
        val uploadStarted = CompletableDeferred<Unit>()
        val allowUploadFinish = CompletableDeferred<Unit>()
        var mutexLockedDuringUpload = true
        var uploadedFileName: String? = null

        val task = object : ImportExportAsyncTask(activity, progressDialog) {
            override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
                val export = DatabaseExport(context, db.db(), true)
                val backupFileName = export.export()
                doForceUploadToDropbox(context, backupFileName)
                return backupFileName
            }

            override fun uploadBackupToDropbox(context: Context, backupFileName: String) {
                mutexLockedDuringUpload = dbMutex.isLocked
                uploadedFileName = backupFileName
                uploadStarted.complete(Unit)
                runBlocking { allowUploadFinish.await() }
            }

            override fun getSuccessMessage(result: Any?): String? = result?.toString()
        }

        task.execute()
        runBlocking {
            withTimeout(5_000L.milliseconds) {
                uploadStarted.await()
            }
            assertFalse("dbMutex must be released before Dropbox upload starts", mutexLockedDuringUpload)

            var concurrentDbOperationCompleted = false
            withTimeout(1_000L.milliseconds) {
                ImportExportAsyncTask.dbMutex.withLock {
                    concurrentDbOperationCompleted = true
                }
            }
            assertTrue(
                "Concurrent dbMutex consumer (e.g. purge/auto-backup) must not be blocked",
                concurrentDbOperationCompleted,
            )
        }

        allowUploadFinish.complete(Unit)
        val result = task.get()
        ShadowLooper.idleMainLooper()

        assertNotNull(uploadedFileName)
        assertEquals(uploadedFileName, result)
    }

    @Test
    fun executeWork_doUploadToDropbox_runsOutsideDbMutex_doesNotBlockConcurrentDbLock() {
        MyPreferences.storeDropboxKeys(activity, "test_token")
        PreferenceManager.getDefaultSharedPreferences(activity)
            .edit()
            .putBoolean("dropbox_upload_backup", true)
            .commit()

        try {
            val uploadStarted = CompletableDeferred<Unit>()
            val allowUploadFinish = CompletableDeferred<Unit>()
            var mutexLockedDuringUpload = true
            var uploadedFileName: String? = null

            val task = object : ImportExportAsyncTask(activity, progressDialog) {
                override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
                    val export = DatabaseExport(context, db.db(), true)
                    val backupFileName = export.export()
                    doUploadToDropbox(context, backupFileName)
                    return backupFileName
                }

                override fun uploadBackupToDropbox(context: Context, backupFileName: String) {
                    mutexLockedDuringUpload = dbMutex.isLocked
                    uploadedFileName = backupFileName
                    uploadStarted.complete(Unit)
                    runBlocking { allowUploadFinish.await() }
                }

                override fun getSuccessMessage(result: Any?): String? = result?.toString()
            }

            task.execute()
            runBlocking {
                withTimeout(5_000L.milliseconds) {
                    uploadStarted.await()
                }
                assertFalse("dbMutex must be released before doUploadToDropbox runs", mutexLockedDuringUpload)

                var concurrentDbOperationCompleted = false
                withTimeout(1_000L.milliseconds) {
                    ImportExportAsyncTask.dbMutex.withLock {
                        concurrentDbOperationCompleted = true
                    }
                }
                assertTrue(
                    "Concurrent dbMutex consumer (e.g. purge/auto-backup) must not be blocked",
                    concurrentDbOperationCompleted,
                )
            }

            allowUploadFinish.complete(Unit)
            val result = task.get()
            ShadowLooper.idleMainLooper()

            assertNotNull(uploadedFileName)
            assertEquals(uploadedFileName, result)
        } finally {
            MyPreferences.removeDropboxKeys(activity)
            PreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .remove("dropbox_upload_backup")
                .commit()
        }
    }

    @Test
    fun dropboxRestoreTask_downloadRunsOutsideDbMutex_doesNotBlockConcurrentDbLock() {
        defaultAccount.title = "BackedUpDropboxAccount"
        db.saveOrUpdate(defaultAccount)
        val backupBytes = DatabaseExport(activity, db.db(), true).generateBackupBytes()

        defaultAccount.title = "MutatedAccountBeforeRestore"
        db.saveOrUpdate(defaultAccount)
        assertEquals("MutatedAccountBeforeRestore", db.getAccount(defaultAccount.id)?.title)

        val downloadStarted = CompletableDeferred<Unit>()
        val allowDownloadFinish = CompletableDeferred<Unit>()
        var mutexLockedDuringDownload = true

        val task = object : DropboxRestoreTask(activity, progressDialog, "remote.backup") {
            override fun downloadBackupFile(context: Context, backupFile: String): ByteArray {
                mutexLockedDuringDownload = dbMutex.isLocked
                downloadStarted.complete(Unit)
                runBlocking { allowDownloadFinish.await() }
                return backupBytes
            }
        }

        task.execute()
        runBlocking {
            withTimeout(5_000L.milliseconds) {
                downloadStarted.await()
            }
            assertFalse("dbMutex must not be held during Dropbox backup download", mutexLockedDuringDownload)

            var concurrentDbOperationCompleted = false
            withTimeout(1_000L.milliseconds) {
                ImportExportAsyncTask.dbMutex.withLock {
                    concurrentDbOperationCompleted = true
                }
            }
            assertTrue(
                "Concurrent dbMutex consumer (e.g. PurgeAccountActivity) must not be blocked",
                concurrentDbOperationCompleted,
            )
        }

        allowDownloadFinish.complete(Unit)
        val result = task.get()
        ShadowLooper.idleMainLooper()

        assertEquals(true, result)
        assertEquals("BackedUpDropboxAccount", db.getAccount(defaultAccount.id)?.title)
    }

    // =========================================================================
    // 2. EXCEPTION HANDLING TESTS
    // =========================================================================

    @Test
    fun workThrowsRuntimeException_caughtAndReturnedAsResult() {
        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            throw IllegalStateException("Fatal SQLite query corruption")
        }

        task.execute()
        val result = task.get()
        ShadowLooper.idleMainLooper()

        assertTrue("Result from task.get() should be the thrown exception", result is IllegalStateException)
        assertEquals("Fatal SQLite query corruption", (result as IllegalStateException).message)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun workThrowsImportExportException_caughtAndShowsErrorDialog() {
        progressDialog.show()
        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            throw ImportExportException(R.string.import_file_not_found)
        }

        task.execute()
        val result = task.get()
        ShadowLooper.idleMainLooper()

        assertTrue("Result should be ImportExportException", result is ImportExportException)
        assertFalse(progressDialog.isShowing)
        val alert = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(alert)
        val shadowAlert = org.robolectric.Shadows.shadowOf(alert)
        assertTrue(shadowAlert.message.contains(activity.getString(R.string.import_file_not_found)))
    }

    @Test
    fun workThrowsAssertionError_terminatesJobAbruptlyWithoutCrashing() {
        var uncaughtThrowable: Throwable? = null
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            uncaughtThrowable = throwable
        }
        val customScope = CoroutineScope(SupervisorJob() + exceptionHandler)

        val task = AdversarialTestTask(activity, progressDialog, customScope) { _, _ ->
            throw AssertionError("Assertion failure in background thread")
        }

        val job = task.execute()
        runBlocking { job.join() }
        assertTrue("Job terminates on error", job.isCompleted)
        assertTrue("Uncaught failure should be AssertionError", uncaughtThrowable is AssertionError)
        assertEquals(
            "Assertion failure in background thread",
            (uncaughtThrowable as AssertionError).message,
        )

        var getError: Throwable? = null
        val getWorker = thread(isDaemon = true) {
            getError = runCatching { task.get() }.exceptionOrNull()
        }
        getWorker.join(1_000L)
        assertTrue("task.get() must return within 1 second", !getWorker.isAlive)
        assertNotNull("Expected task.get() to complete with AssertionError rather than hanging", getError)
        assertEquals("Assertion failure in background thread", getError?.message)
    }

    // =========================================================================
    // 3. ACTIVITY LIFECYCLE: FINISHING AND DESTROYED STATE CHECKS
    // =========================================================================

    @Test
    fun activityFinishing_duringPostExecute_dismissDialogSafelySkipsWithoutThrowing() {
        progressDialog.show()
        assertTrue(progressDialog.isShowing)

        val workStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()

        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            workStarted.complete(Unit)
            allowFinish.await()
            "SuccessResult"
        }

        task.execute()
        runBlocking { workStarted.await() }

        // Finish the activity while background work is running
        activity.finish()
        assertTrue(activity.isFinishing)

        allowFinish.complete(Unit)
        task.get()
        ShadowLooper.idleMainLooper()

        // Verify toast was suppressed because activity is finishing
        assertNull("Toast must not be shown when activity is finishing", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun activityDestroyed_duringPostExecute_dismissDialogSafelySkipsWithoutThrowing() {
        progressDialog.show()
        val workStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()

        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            workStarted.complete(Unit)
            allowFinish.await()
            "SuccessResult"
        }

        task.execute()
        runBlocking { workStarted.await() }

        // Destroy the activity
        activityController.destroy()
        assertTrue(activity.isDestroyed)

        allowFinish.complete(Unit)
        task.get()
        ShadowLooper.idleMainLooper()

        // Verify no toast shown and no crash
        assertNull("Toast must not be shown when activity is destroyed", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun activityDestroyed_whenImportExportException_doesNotShowAlertDialog() {
        val workStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()

        val task = AdversarialTestTask(activity, progressDialog) { _, _ ->
            workStarted.complete(Unit)
            allowFinish.await()
            throw ImportExportException(R.string.import_file_not_found)
        }

        task.execute()
        runBlocking { workStarted.await() }

        // Destroy activity while work in progress
        activityController.destroy()
        assertTrue(activity.isDestroyed)

        allowFinish.complete(Unit)
        task.get()
        ShadowLooper.idleMainLooper()

        // In Android, showing an AlertDialog on a destroyed activity throws BadTokenException.
        // ImportExportAsyncTask checks `if (context.isFinishing || context.isDestroyed) return`.
        assertNull("AlertDialog must not be shown when activity is destroyed", ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun activityDestroyed_publishProgress_skipsDialogSetMessage() {
        progressDialog.show()
        val task = AdversarialTestTask(activity, progressDialog) { _, _ -> "OK" }

        activityController.destroy()
        assertTrue(activity.isDestroyed)

        val initialMessage = org.robolectric.Shadows.shadowOf(progressDialog).message?.toString() ?: ""
        task.callPublishProgress("Updating 50%...")
        Thread.sleep(50)
        ShadowLooper.idleMainLooper()

        val afterMessage = org.robolectric.Shadows.shadowOf(progressDialog).message?.toString() ?: ""
        assertEquals(initialMessage, afterMessage)
    }

    @Test
    fun csvImportTask_execute_with100Rows_updatesProgressDialogMessage() {
        progressDialog.show()
        val tmp = File.createTempFile("test_import_100", ".csv")
        val csvContent = buildString {
            append("date,time,account,amount,currency,category,parent,payee,location,project,note\n")
            repeat(100) { index ->
                append("10.07.2026,07:13:17,Dollar,-10.00,USD,,,,,,note_$index\n")
            }
        }
        tmp.writeText(csvContent)
        registerFileForContentResolver(tmp)

        val options = CsvImportOptions(
            defaultCurrency,
            SimpleDateFormat("dd.MM.yyyy", Locale.US),
            defaultAccount.id,
            WhereFilter.empty(),
            android.net.Uri.fromFile(tmp).toString(),
            ',',
            true,
        )
        val task = CsvImportTask(activity, progressDialog, options)
        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        val message = org.robolectric.Shadows.shadowOf(progressDialog).message?.toString()
        assertEquals(activity.getString(R.string.csv_import_inprogress_update, "100"), message)
    }

    @Test
    fun csvImportTask_onProgressUpdate_viaReflection_withLiveActivity_showsFormattedPercentage() {
        val options = CsvImportOptions(
            defaultCurrency,
            SimpleDateFormat("dd.MM.yyyy", Locale.US),
            defaultAccount.id,
            WhereFilter.empty(),
            "dummy.csv",
            ',',
            true
        )
        val task = CsvImportTask(activity, progressDialog, options)

        progressDialog.show()
        val method = CsvImportTask::class.java.getDeclaredMethod("onProgressUpdate", Array<String>::class.java)
        method.isAccessible = true
        method.invoke(task, arrayOf("50"))
        ShadowLooper.idleMainLooper()

        val message = org.robolectric.Shadows.shadowOf(progressDialog).message?.toString()
        assertEquals(activity.getString(R.string.csv_import_inprogress_update, "50"), message)
    }

    @Test
    fun csvImportTask_onProgressUpdate_viaReflection_withDestroyedActivity_doesNotThrow() {
        val options = CsvImportOptions(
            defaultCurrency,
            SimpleDateFormat("dd.MM.yyyy", Locale.US),
            defaultAccount.id,
            WhereFilter.empty(),
            "dummy.csv",
            ',',
            true
        )
        val task = CsvImportTask(activity, progressDialog, options)

        progressDialog.show()
        activityController.destroy()
        assertTrue(activity.isDestroyed)

        // Publish progress on destroyed activity via reflection
        val method = CsvImportTask::class.java.getDeclaredMethod("onProgressUpdate", Array<String>::class.java)
        method.isAccessible = true
        val initialMessage = org.robolectric.Shadows.shadowOf(progressDialog).message?.toString() ?: ""
        method.invoke(task, arrayOf("50"))
        ShadowLooper.idleMainLooper()
        val afterMessage = org.robolectric.Shadows.shadowOf(progressDialog).message?.toString() ?: ""
        assertEquals(initialMessage, afterMessage)
    }
}
