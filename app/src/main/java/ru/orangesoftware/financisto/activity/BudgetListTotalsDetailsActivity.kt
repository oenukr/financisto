package ru.orangesoftware.financisto.activity

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.BudgetsTotalCalculator
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total

class BudgetListTotalsDetailsActivity : AbstractTotalsDetailsActivity(
    R.string.budget_total_in_currency,
)  {

    private var filter: WhereFilter = WhereFilter.empty()
    private val calculator: BudgetsTotalCalculator by lazy {
        BudgetsTotalCalculator(db, db.getAllBudgets(filter))
    }

    override fun internalOnCreate() {
        filter = WhereFilter.fromIntent(intent)
    }

    override fun prepareInBackground() {
        calculator.updateBudgets(null)
    }

    override fun getTotalInHomeCurrency(): Total = calculator.calculateTotalInHomeCurrency()

    override fun getTotals(): Array<Total> = calculator.calculateTotals()
}
