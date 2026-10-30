// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.db

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CategoryBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.RateBuilder
import ru.orangesoftware.financisto.test.TransactionBuilder
import kotlin.coroutines.CoroutineContext

class BudgetsTotalCalculatorTest : AbstractDbTest() {

    private lateinit var homeCurrency: Currency
    private lateinit var foreignCurrency: Currency
    private lateinit var homeAccount: Account
    private lateinit var foreignAccount: Account
    private lateinit var categoriesMap: Map<String, Category>
    private lateinit var project1: Project
    private lateinit var project2: Project

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

        project1 = Project().apply {
            title = "P1"
            isActive = true
        }
        db.saveOrUpdate(project1)

        project2 = Project().apply {
            title = "P2"
            isActive = true
        }
        db.saveOrUpdate(project2)
    }

    @Test
    fun updateBudgets_populatesSpentAndLabelsWithoutDispatcher() = runBlocking {
        val catA = requireNotNull(categoriesMap["A"])
        val budget = createBudget(
            currency = homeCurrency,
            amount = 1000L,
            categories = catA.id.toString(),
            projects = project1.id.toString(),
        )

        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2011, 4, 10).atNoon())
            .amount(-250L)
            .category(catA)
            .project(project1.title)
            .create()

        val calculator = BudgetsTotalCalculator(db, listOf(budget))
        calculator.updateBudgets()

        assertTrue(budget.updated)
        assertEquals(-250L, budget.spent)
        assertEquals("A", budget.categoriesText)
        assertEquals("P1", budget.projectsText)
    }

    @Test
    fun updateBudgets_populatesSpentAndLabelsOnProvidedMainDispatcher() = runBlocking {
        val catA = requireNotNull(categoriesMap["A"])
        val catB = requireNotNull(categoriesMap["B"])
        val budget = createBudget(
            currency = homeCurrency,
            amount = 2000L,
            categories = "${catA.id},${catB.id}",
            projects = "${project1.id},${project2.id}",
        )

        var mainDispatcherCalls = 0
        val trackingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                mainDispatcherCalls++
                assertFalse(budget.updated)
                assertEquals(0L, budget.spent)
                assertEquals("", budget.categoriesText)
                assertEquals("", budget.projectsText)
                block.run()
                assertTrue(budget.updated)
                assertEquals(-400L, budget.spent)
                assertEquals("A,B", budget.categoriesText)
                assertEquals("P1,P2", budget.projectsText)
            }
        }

        TransactionBuilder.withDb(db)
            .account(homeAccount)
            .dateTime(DateTime.date(2011, 4, 15).atNoon())
            .amount(-400L)
            .category(catA)
            .create()

        val calculator = BudgetsTotalCalculator(db, listOf(budget))
        calculator.updateBudgets(trackingDispatcher)

        assertEquals(1, mainDispatcherCalls)
        assertTrue(budget.updated)
        assertEquals(-400L, budget.spent)
        assertEquals("A,B", budget.categoriesText)
        assertEquals("P1,P2", budget.projectsText)
    }

    @Test
    fun updateBudgets_whenNoCategoriesOrProjects_setsNullLabels() = runBlocking {
        val budget = createBudget(
            currency = homeCurrency,
            amount = 500L,
            categories = "",
            projects = "",
        )

        val calculator = BudgetsTotalCalculator(db, listOf(budget))
        calculator.updateBudgets()

        assertTrue(budget.updated)
        assertNull(budget.categoriesText)
        assertNull(budget.projectsText)
    }

    @Test
    fun updateBudgets_whenCancelled_throwsCancellationException() = runBlocking {
        val catA = requireNotNull(categoriesMap["A"])
        val budget1 = createBudget(
            currency = homeCurrency,
            amount = 1000L,
            categories = catA.id.toString(),
            projects = null,
        )
        val budget2 = createBudget(
            currency = homeCurrency,
            amount = 2000L,
            categories = catA.id.toString(),
            projects = null,
        )
        val job = Job()
        val cancellingDb = object : DatabaseAdapter(context) {
            override fun getCategoriesList(includeNoCategory: Boolean): List<Category> =
                db.getCategoriesList(includeNoCategory)

            override fun getAllProjectsList(includeNoProject: Boolean): ArrayList<Project> =
                db.getAllProjectsList(includeNoProject)

            override fun fetchBudgetBalance(
                categories: Map<Long, Category>?,
                projects: Map<Long, Project>?,
                b: Budget?,
            ): Long {
                val balance = db.fetchBudgetBalance(categories, projects, b)
                if (b === budget1) {
                    job.cancel()
                }
                return balance
            }
        }
        val calculator = BudgetsTotalCalculator(cancellingDb, listOf(budget1, budget2))

        try {
            withContext(job + Dispatchers.Unconfined) {
                calculator.updateBudgets()
            }
            fail("Expected CancellationException to be thrown")
        } catch (expected: CancellationException) {
            assertTrue(budget1.updated)
            assertFalse(budget2.updated)
        }
    }

    @Test
    fun calculateTotals_aggregatesBudgetsByCurrency() = runBlocking {
        val catA = requireNotNull(categoriesMap["A"])
        val b1 = createBudget(currency = homeCurrency, amount = 1000L, categories = catA.id.toString(), projects = null)
        val b2 = createBudget(currency = homeCurrency, amount = 500L, categories = catA.id.toString(), projects = null)
        val b3 = createBudget(currency = foreignCurrency, amount = 800L, categories = catA.id.toString(), projects = null)

        b1.spent = -300L
        b2.spent = -100L
        b3.spent = -200L

        val calculator = BudgetsTotalCalculator(db, listOf(b1, b2, b3))
        val totals = calculator.calculateTotals().associateBy { it.currency.id }

        assertEquals(2, totals.size)
        val usdTotal = requireNotNull(totals[homeCurrency.id])
        assertEquals(-400L, usdTotal.amount)
        assertEquals(1100L, usdTotal.balance)

        val eurTotal = requireNotNull(totals[foreignCurrency.id])
        assertEquals(-200L, eurTotal.amount)
        assertEquals(600L, eurTotal.balance)
    }

    @Test
    fun calculateTotalInHomeCurrency_convertsUsingLatestRates() {
        RateBuilder.withDb(db)
            .from(foreignCurrency)
            .to(homeCurrency)
            .at(DateTime.date(2011, 4, 1))
            .rate(2.0f)
            .create()

        val catA = requireNotNull(categoriesMap["A"])
        val bHome = createBudget(currency = homeCurrency, amount = 1000L, categories = catA.id.toString(), projects = null)
        val bForeign = createBudget(currency = foreignCurrency, amount = 500L, categories = catA.id.toString(), projects = null)
        bHome.spent = -200L
        bForeign.spent = -100L

        val calculator = BudgetsTotalCalculator(db, listOf(bHome, bForeign))
        val homeTotal = calculator.calculateTotalInHomeCurrency()

        assertFalse(homeTotal.isError)
        assertEquals(homeCurrency.id, homeTotal.currency.id)
        // amount = -200 + (2.0 * -100) = -400
        assertEquals(-400L, homeTotal.amount)
        // balance = (1000 - 200) + 2.0 * (500 - 100) = 800 + 800 = 1600
        assertEquals(1600L, homeTotal.balance)
    }

    @Test
    fun calculateTotalInHomeCurrency_whenRateMissing_returnsTotalWithError() {
        val catA = requireNotNull(categoriesMap["A"])
        val bForeign = createBudget(currency = foreignCurrency, amount = 500L, categories = catA.id.toString(), projects = null)
        bForeign.spent = -100L

        val calculator = BudgetsTotalCalculator(db, listOf(bForeign))
        val homeTotal = calculator.calculateTotalInHomeCurrency()

        assertTrue(homeTotal.isError)
        assertNotNull(homeTotal.error)
        assertEquals(foreignCurrency.id, homeTotal.error?.currency?.id)
    }

    private fun createBudget(
        currency: Currency,
        amount: Long,
        categories: String?,
        projects: String?,
    ): Budget {
        val budget = Budget().apply {
            this.currency = currency
            this.amount = amount
            this.categories = categories
            this.projects = projects
            this.expanded = true
            this.includeSubcategories = true
            this.startDate = DateTime.date(2011, 4, 1).atMidnight().asLong()
            this.endDate = DateTime.date(2011, 4, 30).at(23, 59, 59, 999).asLong()
        }
        db.saveOrUpdate(budget)
        return budget
    }
}
