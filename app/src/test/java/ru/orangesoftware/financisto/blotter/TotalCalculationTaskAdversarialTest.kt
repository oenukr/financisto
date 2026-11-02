// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.blotter

import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.RateBuilder
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache

@Suppress("MagicNumber", "TooManyFunctions")
class TotalCalculationTaskAdversarialTest : AbstractDbTest() {

    private lateinit var usdCurrency: Currency
    private lateinit var eurCurrency: Currency
    private lateinit var gbpCurrency: Currency
    private lateinit var usdAccount: Account
    private lateinit var eurAccount: Account
    private lateinit var gbpAccount: Account

    @Before
    override fun setUp() {
        super.setUp()
        usdCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("US Dollar")
            .symbol("$")
            .makeDefault()
            .create()

        eurCurrency = CurrencyBuilder.withDb(db)
            .name("EUR")
            .title("Euro")
            .symbol("€")
            .create()

        gbpCurrency = CurrencyBuilder.withDb(db)
            .name("GBP")
            .title("British Pound")
            .symbol("£")
            .create()

        usdAccount = AccountBuilder.createDefault(db, usdCurrency)
        eurAccount = AccountBuilder.createDefault(db, eurCurrency)
        gbpAccount = AccountBuilder.createDefault(db, gbpCurrency)
        CurrencyCache.initialize(db)
    }

    @Test
    fun cancel_duringInFlightCalculation_cancelsJobAndAbortsPostExecute() = runBlocking {
        val textView = TextView(context).apply { text = "OriginalText" }
        val calculationStarted = CompletableDeferred<Unit>()
        val allowCalculationToFinish = CompletableDeferred<Unit>()

        val blockingTask = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                runBlocking {
                    calculationStarted.complete(Unit)
                    allowCalculationToFinish.await()
                }
                val total = Total(usdCurrency)
                total.balance = 99999L
                return total
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = blockingTask.execute(this)

        // Wait until calculation starts on IO dispatcher
        calculationStarted.await()

        // Cancel the task while in-flight
        val wasActive = blockingTask.cancel()
        assertTrue("Expected cancel to report true for active job", wasActive)
        assertTrue(blockingTask.isCancelled)
        assertTrue(job.isCancelled)

        // Unblock the calculation
        allowCalculationToFinish.complete(Unit)
        job.join()
        ShadowLooper.idleMainLooper()

        // Verify TextView was NOT updated and lastResult was NOT set
        assertEquals("OriginalText", textView.text.toString())
        assertNull(blockingTask.lastResult)
    }

    @Test
    fun cancel_beforeExecution_abortsImmediatelyWithoutRunningCalculation() = runBlocking {
        var calculationRan = false
        val textView = TextView(context).apply { text = "Unmodified" }

        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                calculationRan = true
                return Total(usdCurrency)
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        task.cancel()
        assertTrue(task.isCancelled)

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertFalse("Calculation should not have run after pre-cancellation", calculationRan)
        assertEquals("Unmodified", textView.text.toString())
        assertNull(task.lastResult)
    }

    @Test
    fun cancellationException_isRethrown_notSwallowedAsGenericError() = runBlocking {
        val textView = TextView(context)
        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                throw CancellationException("Simulated coroutine cancellation")
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertTrue(job.isCancelled)
        // Ensure lastResult is not Total.ZERO (which would happen if caught as Exception)
        assertNull(task.lastResult)
    }

    @Test
    fun multipleRapidExecutions_cancelsPreviousJobs_andOnlyLastCompletes() = runBlocking {
        val textView = TextView(context)
        val results = listOf(100L, 200L, 300L, 400L, 500L)
        var lastCompletedAmount = 0L

        class RapidTask(private val amount: Long) : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                val t = Total(usdCurrency)
                t.balance = amount
                return t
            }

            override fun getTotals(): Array<Total> = emptyArray()

            override fun onPostExecute(result: Total) {
                super.onPostExecute(result)
                lastCompletedAmount = result.balance
            }
        }

        var currentTask: RapidTask? = null
        val tasks = mutableListOf<RapidTask>()
        val jobs = mutableListOf<Job>()
        for (amount in results) {
            currentTask?.cancel()
            val task = RapidTask(amount)
            currentTask = task
            tasks.add(task)
            jobs.add(task.execute(this))
        }
        jobs.forEach { it.join() }
        ShadowLooper.idleMainLooper()

        // The final task executed must be the one that completed successfully
        val lastTask = tasks.last()
        assertEquals(500L, lastTask.lastResult?.balance)
        assertEquals(500L, lastCompletedAmount)
        val text = textView.text.toString()
        assertTrue("Expected 5.00 in text view, got: $text", text.contains("5.00"))
    }

    @Test
    fun sameTaskInstance_multipleRapidExecutions_cancelsPreviousJob() = runBlocking {
        val textView = TextView(context)
        var counter = 0L

        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                val t = Total(usdCurrency)
                t.balance = ++counter
                return t
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job1 = task.execute(this)
        val job2 = task.execute(this)
        val job3 = task.execute(this)

        job1.join()
        job2.join()
        job3.join()
        ShadowLooper.idleMainLooper()

        assertTrue("First job should be cancelled", job1.isCancelled)
        assertTrue("Second job should be cancelled", job2.isCancelled)
        assertFalse("Final job should complete normally", job3.isCancelled)
        assertNotNull(task.lastResult)
    }

    @Test
    fun multiCurrency_threeCurrencies_accurateConversion() {
        RateBuilder.withDb(db)
            .from(eurCurrency)
            .to(usdCurrency)
            .at(DateTime.date(2026, 1, 1))
            .rate(1.20f)
            .create()

        RateBuilder.withDb(db)
            .from(gbpCurrency)
            .to(usdCurrency)
            .at(DateTime.date(2026, 1, 1))
            .rate(1.50f)
            .create()

        // USD: 1000 ($10.00)
        TransactionBuilder.withDb(db)
            .account(usdAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1000L)
            .create()

        // EUR: 2000 (€20.00) -> 2000 * 1.20 = 2400 USD
        TransactionBuilder.withDb(db)
            .account(eurAccount)
            .dateTime(DateTime.date(2026, 1, 11).atNoon())
            .amount(2000L)
            .create()

        // GBP: 3000 (£30.00) -> 3000 * 1.50 = 4500 USD
        TransactionBuilder.withDb(db)
            .account(gbpAccount)
            .dateTime(DateTime.date(2026, 1, 12).atNoon())
            .amount(3000L)
            .create()

        val task = BlotterTotalCalculationTask(context, db, WhereFilter.empty(), null)
        val totalInHome = task.getTotalInHomeCurrency()

        assertEquals(usdCurrency.id, totalInHome.currency.id)
        // Expected: 1000 + 2400 + 4500 = 7900
        assertEquals(7900L, totalInHome.balance)

        val totals = task.getTotals()
        assertEquals(3, totals.size)
        val usdTotal = totals.firstOrNull { it.currency.id == usdCurrency.id }
        val eurTotal = totals.firstOrNull { it.currency.id == eurCurrency.id }
        val gbpTotal = totals.firstOrNull { it.currency.id == gbpCurrency.id }

        assertNotNull(usdTotal)
        assertNotNull(eurTotal)
        assertNotNull(gbpTotal)
        assertEquals(1000L, usdTotal?.balance)
        assertEquals(2000L, eurTotal?.balance)
        assertEquals(3000L, gbpTotal?.balance)
    }

    @Test
    fun multiCurrency_withMissingExchangeRate_returnsTotalErrorAndDisplaysNA() = runBlocking {
        // Create EUR transaction without exchange rate to USD
        TransactionBuilder.withDb(db)
            .account(eurAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1500L)
            .create()

        val textView = TextView(context)
        val task = BlotterTotalCalculationTask(context, db, WhereFilter.empty(), textView)

        val total = task.getTotalInHomeCurrency()
        assertTrue("Total must indicate rate calculation error", total.isError)

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        val text = textView.text.toString()
        val expectedNA = context.getString(R.string.not_available)
        assertEquals(expectedNA, text)
        assertNotNull("Expected error drawable to be set on text view", textView.error)
    }

    @Test
    fun nullContextAndNullTextView_executeGracefullyWithoutExceptions() = runBlocking {
        val task = object : TotalCalculationTask(null, null) {
            override fun getTotalInHomeCurrency(): Total {
                val t = Total(usdCurrency)
                t.balance = 4200L
                return t
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertEquals(4200L, task.lastResult?.balance)
    }

    @Test
    fun unexpectedException_logsError_setsTotalZero_updatesTextView() = runBlocking {
        val textView = TextView(context)
        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                error("Corrupted database disk error")
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertEquals(Total.ZERO, task.lastResult)
        val text = textView.text.toString()
        assertTrue("Expected 0.00 in text view for Total.ZERO, got: $text", text.contains("0.00"))
    }

    @Test
    fun concurrencyStress_parallelExecutionsAndCancellations() = runBlocking {
        val textView = TextView(context)
        val parallelTasks = (1..20).map { i ->
            object : TotalCalculationTask(context, textView) {
                override fun getTotalInHomeCurrency(): Total {
                    val t = Total(usdCurrency)
                    t.balance = i * 100L
                    return t
                }

                override fun getTotals(): Array<Total> = emptyArray()
            }
        }

        val deferreds = parallelTasks.mapIndexed { index, task ->
            async(Dispatchers.Default) {
                val job = task.execute(this)
                if (index % 2 == 0) {
                    task.cancel()
                }
                job.join()
            }
        }

        deferreds.awaitAll()
        ShadowLooper.idleMainLooper()

        // Verify that cancelled tasks have no lastResult or isCancelled == true
        parallelTasks.forEachIndexed { index, task ->
            if (index % 2 == 0) {
                assertTrue("Even indexed task should be cancelled", task.isCancelled)
            } else {
                assertTrue("Odd indexed task should complete", task.lastResult != null)
            }
        }
    }

    @Test
    fun sqliteException_caughtAndLogged_returnsTotalZero() = runBlocking {
        val textView = TextView(context)
        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                throw android.database.sqlite.SQLiteException("disk I/O error (code 778)")
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertEquals(Total.ZERO, task.lastResult)
        val text = textView.text.toString()
        assertTrue("Expected 0.00 for Total.ZERO, got: $text", text.contains("0.00"))
    }

    @Test
    fun cursorWindowAllocationException_caughtAndLogged_returnsTotalZero() = runBlocking {
        val textView = TextView(context)
        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                throw android.database.CursorWindowAllocationException(
                    "Could not allocate CursorWindow of size 2097152 due to OOM"
                )
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertEquals(Total.ZERO, task.lastResult)
        val text = textView.text.toString()
        assertTrue("Expected 0.00 for Total.ZERO, got: $text", text.contains("0.00"))
    }

    @Test
    fun multiCurrency_withZeroExchangeRate_convertsCorrectlyToZeroBalance() = runBlocking {
        RateBuilder.withDb(db)
            .from(eurCurrency)
            .to(usdCurrency)
            .at(DateTime.date(2026, 1, 1))
            .rate(0.0f)
            .create()

        TransactionBuilder.withDb(db)
            .account(eurAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(5000L)
            .create()

        val textView = TextView(context)
        val task = BlotterTotalCalculationTask(context, db, WhereFilter.empty(), textView)

        val total = task.getTotalInHomeCurrency()
        assertFalse("Total should not be error when rate is 0.0", total.isError)
        assertEquals(0L, total.balance)

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        val text = textView.text.toString()
        assertTrue("Expected 0.00 in text view for 0.0 rate, got: $text", text.contains("0.00"))
    }

    @Test
    fun cancellationDuringDbQuery_whenDbClosedConcurrently_doesNotThrowUnhandledException() = runBlocking {
        val textView = TextView(context).apply { text = "BeforeDestroy" }
        val calculationStarted = CompletableDeferred<Unit>()
        val dbClosedSignal = CompletableDeferred<Unit>()

        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                runBlocking {
                    calculationStarted.complete(Unit)
                    dbClosedSignal.await()
                }
                throw android.database.sqlite.SQLiteException(
                    "Cannot perform this operation because the connection pool has been closed"
                )
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        calculationStarted.await()

        task.cancel()
        dbClosedSignal.complete(Unit)

        job.join()
        ShadowLooper.idleMainLooper()

        assertTrue(task.isCancelled)
        assertTrue(job.isCancelled)
        assertEquals("BeforeDestroy", textView.text.toString())
        assertNull(task.lastResult)
    }

    @Test
    fun cooperativeCancellation_ensureActive_preventsUIUpdateEvenIfQueryFinished() = runBlocking {
        val textView = TextView(context).apply { text = "OldValue" }
        val queryDone = CompletableDeferred<Unit>()
        val allowResume = CompletableDeferred<Unit>()

        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                val t = Total(usdCurrency)
                t.balance = 8888L
                runBlocking {
                    queryDone.complete(Unit)
                    allowResume.await()
                }
                return t
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        queryDone.await()

        task.cancel()
        allowResume.complete(Unit)

        job.join()
        ShadowLooper.idleMainLooper()

        assertTrue(job.isCancelled)
        assertEquals("OldValue", textView.text.toString())
        assertNull(task.lastResult)
    }
}
