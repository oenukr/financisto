// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportTask
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import java.text.SimpleDateFormat
import java.util.Locale

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
    fun csvImportTask_publishProgress_withDestroyedActivity_doesNotThrow() {
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
