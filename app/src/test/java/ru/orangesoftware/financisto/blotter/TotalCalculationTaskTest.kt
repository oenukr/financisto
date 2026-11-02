// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.blotter

import android.widget.TextView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AccountListActivity
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CategoryBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.RateBuilder
import ru.orangesoftware.financisto.test.TransactionBuilder

class TotalCalculationTaskTest : AbstractDbTest() {

    private lateinit var homeCurrency: Currency
    private lateinit var foreignCurrency: Currency
    private lateinit var homeAccount: Account
    private lateinit var foreignAccount: Account
    private lateinit var categoriesMap: Map<String, Category>

    @Before
    override fun setUp() {
        super.setUp()
        homeCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("US Dollar")
            .symbol("$")
            .makeDefault()
            .create()

        foreignCurrency = CurrencyBuilder.withDb(db)
            .name("EUR")
            .title("Euro")
            .symbol("€")
            .create()

        homeAccount = AccountBuilder.createDefault(db, homeCurrency)
        foreignAccount = AccountBuilder.createDefault(db, foreignCurrency)
        categoriesMap = CategoryBuilder.createDefaultHierarchy(db)
    }

    @Test
    fun accountTotalCalculationTask_returnsTotalsAndHomeCurrencyBalance() {
        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1000L)
            .create()

        val filter = WhereFilter.empty().eq(BlotterFilter.FROM_ACCOUNT_ID, homeAccount.id.toString())
        val task = AccountTotalCalculationTask(context, db, filter, null)

        val homeTotal = task.getTotalInHomeCurrency()
        assertEquals(homeCurrency.id, homeTotal.currency.id)
        assertEquals(1000L, homeTotal.balance)

        val totals = task.getTotals()
        assertEquals(1, totals.size)
        assertEquals(homeCurrency.id, totals[0].currency.id)
        assertEquals(1000L, totals[0].balance)
    }

    @Test
    fun accountTotalCalculationTask_enhancedFilter_excludesSplitParentFromDoubleCounting() {
        val parent = TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2026, 1, 15).atNoon())
            .amount(-500L)
            .create()

        val catA = requireNotNull(categoriesMap["A"])
        val catB = requireNotNull(categoriesMap["B"])

        val split1 = Transaction().apply {
            parentId = parent.id
            fromAccountId = homeAccount.id
            dateTime = parent.dateTime
            fromAmount = -300L
            categoryId = catA.id
        }
        db.saveOrUpdate(split1)

        val split2 = Transaction().apply {
            parentId = parent.id
            fromAccountId = homeAccount.id
            dateTime = parent.dateTime
            fromAmount = -200L
            categoryId = catB.id
        }
        db.saveOrUpdate(split2)

        val filter = WhereFilter.empty().eq(BlotterFilter.FROM_ACCOUNT_ID, homeAccount.id.toString())
        val task = AccountTotalCalculationTask(context, db, filter, null)

        val homeTotal = task.getTotalInHomeCurrency()
        assertEquals(-500L, homeTotal.balance)
    }

    @Test
    fun blotterTotalCalculationTask_convertsMultipleCurrenciesToHomeCurrency() {
        RateBuilder.withDb(db)
            .from(foreignCurrency)
            .to(homeCurrency)
            .at(DateTime.date(2026, 1, 1))
            .rate(1.5f)
            .create()

        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1000L)
            .create()

        TransactionBuilder.withDb(db)
            .account(foreignAccount)
            .dateTime(DateTime.date(2026, 1, 12).atNoon())
            .amount(2000L)
            .create()

        val task = BlotterTotalCalculationTask(context, db, WhereFilter.empty(), null)

        val homeTotal = task.getTotalInHomeCurrency()
        assertEquals(homeCurrency.id, homeTotal.currency.id)
        // 1000 USD + (2000 EUR * 1.5) = 1000 + 3000 = 4000 USD
        assertEquals(4000L, homeTotal.balance)

        val totals = task.getTotals()
        assertEquals(2, totals.size)
        val usdTotal = totals.firstOrNull { it.currency.id == homeCurrency.id }
        val eurTotal = totals.firstOrNull { it.currency.id == foreignCurrency.id }
        assertNotNull(usdTotal)
        assertNotNull(eurTotal)
        assertEquals(1000L, usdTotal?.balance)
        assertEquals(2000L, eurTotal?.balance)
    }

    @Test
    fun accountTotalsCalculationTask_matchesDatabaseAccountsTotalInHomeCurrency() {
        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1500L)
            .create()

        val constructor = AccountListActivity.AccountTotalsCalculationTask::class.java.getDeclaredConstructor(
            android.content.Context::class.java,
            ru.orangesoftware.financisto.db.DatabaseAdapter::class.java,
            TextView::class.java
        ).apply { isAccessible = true }
        val task = constructor.newInstance(context, db, null)
        val homeTotal = task.getTotalInHomeCurrency()
        val expectedTotal = db.accountsTotalInHomeCurrency

        assertEquals(expectedTotal.balance, homeTotal.balance)
        assertEquals(expectedTotal.currency.id, homeTotal.currency.id)

        val totals = task.getTotals()
        assertEquals(0, totals.size)
    }

    @Test
    fun budgetBlotterCalculationTask_calculatesBudgetBalanceWithFilters() {
        val catA = requireNotNull(categoriesMap["A"])
        val project = Project().apply {
            title = "TestProj"
            isActive = true
        }
        db.saveOrUpdate(project)

        val budget = Budget().apply {
            currency = homeCurrency
            amount = 5000L
            categories = catA.id.toString()
            projects = project.id.toString()
            expanded = true
            includeSubcategories = true
            startDate = DateTime.date(2026, 1, 1).atMidnight().asLong()
            endDate = DateTime.date(2026, 1, 31).at(23, 59, 59, 999).asLong()
        }
        db.saveOrUpdate(budget)

        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2026, 1, 15).atNoon())
            .amount(-1200L)
            .category(catA)
            .project(project.title)
            .create()

        val categories = mapOf(catA.id to catA)
        val projects = mapOf(project.id to project)

        val budgetTask = object : TotalCalculationTask(context, null) {
            override fun getTotalInHomeCurrency(): Total {
                val b = db.load(Budget::class.java, budget.id)
                val total = Total(b.budgetCurrency)
                total.balance = db.fetchBudgetBalance(categories, projects, b)
                return total
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val budgetTotal = budgetTask.getTotalInHomeCurrency()
        assertEquals(homeCurrency.id, budgetTotal.currency.id)
        assertEquals(-1200L, budgetTotal.balance)
        assertEquals(0, budgetTask.getTotals().size)
    }

    @Test
    fun totalCalculationTask_asyncExecution_updatesTextView() = runBlocking {
        val textView = TextView(context)
        val filter = WhereFilter.empty().eq(BlotterFilter.FROM_ACCOUNT_ID, homeAccount.id.toString())

        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(2500L)
            .create()

        val task = AccountTotalCalculationTask(context, db, filter, textView)
        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        val renderedText = textView.text.toString()
        assertTrue("Expected rendered text to contain 25.00, got: $renderedText", renderedText.contains("25.00"))
    }

    @Test
    fun totalCalculationTask_whenCurrencyIsEmpty_showsWarningToast() = runBlocking {
        val textView = TextView(context)
        val emptyCurrencyTask = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total = Total(Currency.EMPTY)
            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = emptyCurrencyTask.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        val toastText = ShadowToast.getTextOfLatestToast()
        assertEquals(context.getString(R.string.currency_make_default_warning), toastText)
    }

    @Test
    fun totalCalculationTask_whenStopped_doesNotUpdateTextView() = runBlocking {
        val textView = TextView(context).apply { text = "InitialState" }
        val filter = WhereFilter.empty().eq(BlotterFilter.FROM_ACCOUNT_ID, homeAccount.id.toString())

        val task = AccountTotalCalculationTask(context, db, filter, textView)
        task.stop()
        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertEquals("InitialState", textView.text.toString())
    }

    @Test
    fun totalCalculationTask_whenCalculationThrows_recoversWithTotalZero() = runBlocking {
        val textView = TextView(context)
        val task = object : TotalCalculationTask(context, textView) {
            override fun getTotalInHomeCurrency(): Total {
                throw IllegalStateException("Simulated query failure")
            }

            override fun getTotals(): Array<Total> = emptyArray()
        }

        val job = task.execute(this)
        job.join()
        ShadowLooper.idleMainLooper()

        assertEquals(Total.ZERO, task.lastResult)
    }
}
