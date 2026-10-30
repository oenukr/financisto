// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.MyEntity
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.model.TotalError
import ru.orangesoftware.financisto.rates.ExchangeRate
import ru.orangesoftware.financisto.utils.Logger
import java.math.BigDecimal

class BudgetsTotalCalculator(
    private val db: DatabaseAdapter,
    private val budgets: List<Budget>,
) {

    private val logger: Logger = DependenciesHolder().logger

    suspend fun updateBudgets(mainDispatcher: CoroutineDispatcher? = null) {
        val t0 = System.currentTimeMillis()
        try {
            val categories: Map<Long, Category> = MyEntity.asMap(db.getCategoriesList(true))
            val projects: Map<Long, Project> = MyEntity.asMap(db.getAllProjectsList(true))
            for (b in budgets) {
                currentCoroutineContext().ensureActive()
                val spent = db.fetchBudgetBalance(categories, projects, b)
                val categoriesText = getChecked(categories, b.categories)
                val projectsText = getChecked(projects, b.projects)
                if (mainDispatcher != null) {
                    withContext(mainDispatcher) {
                        b.updated = true
                        b.spent = spent
                        b.categoriesText = categoriesText
                        b.projectsText = projectsText
                    }
                } else {
                    b.updated = true
                    b.spent = spent
                    b.categoriesText = categoriesText
                    b.projectsText = projectsText
                }
            }
        } finally {
            val t1 = System.currentTimeMillis()
            logger.d("BUDGET UPDATE", "${t1 - t0}ms")
        }
    }

    fun calculateTotals(): Array<Total> {
        val totals = HashMap<Currency, Total>()
        for (b in budgets) {
            val c = b.budgetCurrency
            val total = totals.getOrPut(c) { Total(c, true) }
            total.amount += b.spent
            total.balance += b.amount + b.spent
        }
        return totals.values.toTypedArray()
    }

    fun calculateTotalInHomeCurrency(): Total {
        val t0 = System.currentTimeMillis()
        try {
            var amount = BigDecimal.ZERO
            var balance = BigDecimal.ZERO
            val rates = db.latestRates
            val homeCurrency = db.homeCurrency
            for (b in budgets) {
                val currency = b.budgetCurrency
                val r = rates.getRate(currency, homeCurrency)
                if (r == ExchangeRate.NA) {
                    return Total(homeCurrency, TotalError.lastRateError(currency))
                } else {
                    amount = amount.add(convert(r, b.spent))
                    balance = balance.add(convert(r, b.amount + b.spent))
                }
            }
            val total = Total(homeCurrency, true)
            total.amount = amount.toLong()
            total.balance = balance.toLong()
            return total
        } finally {
            val t1 = System.currentTimeMillis()
            logger.d("BUDGET TOTALS", "${t1 - t0}ms")
        }
    }

    private fun convert(r: ExchangeRate, spent: Long): BigDecimal =
        BigDecimal.valueOf(r.rate * spent)

    private fun <T : MyEntity> getChecked(entities: Map<Long, T>, s: String?): String? {
        val ids = MyEntity.splitIds(s) ?: return null
        return if (ids.size == 1) {
            entities[ids[0]]?.title
        } else {
            val sb = StringBuilder()
            for (id in ids) {
                val e = entities[id]
                if (e != null) {
                    if (sb.isNotEmpty()) {
                        sb.append(",")
                    }
                    sb.append(e.title)
                }
            }
            sb.toString().takeIf { it.isNotEmpty() }
        }
    }
}
