// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.BlotterListAdapter
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CategoryBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache

class BlotterActivityTest : AbstractDbTest() {

    private lateinit var homeCurrency: Currency
    private lateinit var account1: Account
    private lateinit var account2: Account

    @Before
    override fun setUp() {
        super.setUp()
        homeCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .separators("','", "'.'")
            .makeDefault()
            .create()

        account1 = AccountBuilder.createDefault(db, homeCurrency)
        account2 = AccountBuilder.createDefault(db, homeCurrency)
        CurrencyCache.initialize(db)
    }

    @Test
    fun blotterActivity_withAccountFilter_calculatesAccountTotalsAndDisplaysTransactions() {
        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1500L)
            .create()

        val intent = Intent()
        val filter = WhereFilter.empty().eq(BlotterFilter.FROM_ACCOUNT_ID, account1.id.toString())
        filter.toIntent(intent)
        intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true)

        val controller = Robolectric.buildActivity(BlotterActivity::class.java, intent).setup()
        val activity = controller.get()
        waitForTotalCalculation(activity)

        val listView = activity.findViewById<ListView>(android.R.id.list)
        assertNotNull(listView)
        assertEquals(1, listView.count)
        assertTrue(listView.adapter is TransactionsListAdapter)

        val totalView = activity.findViewById<TextView>(R.id.total)
        assertNotNull(totalView)
        val text = totalView.text.toString()
        assertTrue("Expected total text to contain 15.00, got: $text", text.contains("15.00"))

        controller.destroy()
    }

    @Test
    fun blotterActivity_generalBlotter_calculatesBlotterTotalsInHomeCurrency() {
        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1000L)
            .create()

        TransactionBuilder.withDb(db)
            .account(account2)
            .dateTime(DateTime.date(2026, 1, 11).atNoon())
            .amount(2000L)
            .create()

        val controller = Robolectric.buildActivity(BlotterActivity::class.java).setup()
        val activity = controller.get()
        waitForTotalCalculation(activity)

        val listView = activity.findViewById<ListView>(android.R.id.list)
        assertNotNull(listView)
        assertEquals(2, listView.count)
        assertTrue(listView.adapter is BlotterListAdapter)

        val totalView = activity.findViewById<TextView>(R.id.total)
        assertNotNull(totalView)
        val text = totalView.text.toString()
        assertTrue("Expected blotter total text to contain 30.00, got: $text", text.contains("30.00"))

        controller.destroy()
    }

    @Test
    fun blotterActivity_totalClick_launchesBlotterTotalsDetailsActivity() {
        val controller = Robolectric.buildActivity(BlotterActivity::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val totalView = activity.findViewById<TextView>(R.id.total)
        totalView.performClick()

        val shadowActivity = shadowOf(activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(BlotterTotalsDetailsActivity::class.java.name, startedIntent.component?.className)

        controller.destroy()
    }

    @Test
    fun blotterActivity_recreateCursor_recalculatesTotals() {
        val controller = Robolectric.buildActivity(BlotterActivity::class.java).setup()
        val activity = controller.get()
        waitForTotalCalculation(activity)

        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(5500L)
            .create()

        activity.recreateCursor()
        waitForTotalCalculation(activity)

        val totalView = activity.findViewById<TextView>(R.id.total)
        val text = totalView.text.toString()
        assertTrue("Expected recalculated total text to contain 55.00, got: $text", text.contains("55.00"))

        controller.destroy()
    }

    @Test
    fun blotterActivity_searchFilter_filtersTransactionsAndUpdatesTotals() {
        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(2500L)
            .note("Groceries Store")
            .create()

        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 11).atNoon())
            .amount(7500L)
            .note("Electronics Shop")
            .create()

        val controller = Robolectric.buildActivity(BlotterActivity::class.java).setup()
        val activity = controller.get()
        waitForTotalCalculation(activity)

        val searchButton = activity.findViewById<View>(R.id.bSearch)
        searchButton.performClick()

        val searchLayout = activity.findViewById<FrameLayout>(R.id.search_text_frame)
        assertEquals(View.VISIBLE, searchLayout.visibility)

        val searchText = activity.findViewById<EditText>(R.id.search_text)
        searchText.setText("Groceries")
        waitForTotalCalculation(activity)

        val listView = activity.findViewById<ListView>(android.R.id.list)
        assertEquals(1, listView.count)

        val totalView = activity.findViewById<TextView>(R.id.total)
        val text = totalView.text.toString()
        assertTrue("Expected total text to contain 25.00, got: $text", text.contains("25.00"))

        controller.destroy()
    }

    @Test
    fun splitsBlotterActivity_launchesAndCalculatesTotals() {
        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(3200L)
            .create()

        val intent = Intent()
        val filter = WhereFilter.empty().eq(BlotterFilter.FROM_ACCOUNT_ID, account1.id.toString())
        filter.toIntent(intent)

        val controller = Robolectric.buildActivity(SplitsBlotterActivity::class.java, intent).setup()
        val activity = controller.get()
        waitForTotalCalculation(activity)

        val filterButton = activity.findViewById<View>(R.id.bFilter)
        assertEquals(View.GONE, filterButton.visibility)

        val listView = activity.findViewById<ListView>(android.R.id.list)
        assertEquals(1, listView.count)
        assertTrue(listView.adapter is TransactionsListAdapter)

        val totalView = activity.findViewById<TextView>(R.id.total)
        val text = totalView.text.toString()
        assertTrue("Expected splits blotter total to contain 32.00, got: $text", text.contains("32.00"))

        controller.destroy()
    }

    @Test
    fun budgetBlotterActivity_launchesAndCalculatesBudgetBalance() {
        val categories = CategoryBuilder.createDefaultHierarchy(db)
        val catA = requireNotNull(categories["A"])

        val budget = Budget().apply {
            this.currency = homeCurrency
            this.amount = 5000L
            this.categories = catA.id.toString()
            this.expanded = true
            this.includeSubcategories = true
            this.startDate = DateTime.date(2026, 1, 1).atMidnight().asLong()
            this.endDate = DateTime.date(2026, 1, 31).at(23, 59, 59, 999).asLong()
        }
        db.saveOrUpdate(budget)

        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 15).atNoon())
            .amount(-1500L)
            .category(catA)
            .create()

        val intent = Intent()
        val filter = WhereFilter.empty().eq(BlotterFilter.BUDGET_ID, budget.id.toString())
        filter.toIntent(intent)

        val controller = Robolectric.buildActivity(BudgetBlotterActivity::class.java, intent).setup()
        val activity = controller.get()
        waitForTotalCalculation(activity)

        val filterButton = activity.findViewById<View>(R.id.bFilter)
        assertEquals(View.GONE, filterButton.visibility)

        val totalView = activity.findViewById<TextView>(R.id.total)
        val text = totalView.text.toString()
        val expectedNegativeAmount = CurrencyCache.createCurrencyFormat(homeCurrency).format(-15L)
        assertEquals("$expectedNegativeAmount $", text)

        controller.destroy()
    }

    private fun waitForTotalCalculation(activity: BlotterActivity) {
        var attempts = 0
        while (activity.calculationTask?.job?.isCompleted == false && attempts < MAX_WAIT_ATTEMPTS) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(POLL_INTERVAL_MS)
            attempts++
        }
        ShadowLooper.idleMainLooper()
    }

    companion object {
        private const val MAX_WAIT_ATTEMPTS = 100
        private const val POLL_INTERVAL_MS = 10L
    }
}
