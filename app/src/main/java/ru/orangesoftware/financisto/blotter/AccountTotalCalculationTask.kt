package ru.orangesoftware.financisto.blotter

import android.content.Context
import android.widget.TextView
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseAdapter.enhanceFilterForAccountBlotter
import ru.orangesoftware.financisto.db.TransactionsTotalCalculator
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total

class AccountTotalCalculationTask(
    context: Context,
    private val db: DatabaseAdapter,
    whereFilter: WhereFilter,
    totalText: TextView?,
) : TotalCalculationTask(context, totalText) {

    private val filter: WhereFilter = enhanceFilterForAccountBlotter(whereFilter)

    override fun getTotalInHomeCurrency(): Total =
        TransactionsTotalCalculator(db, filter).accountTotal

    override fun getTotals(): Array<Total> =
        TransactionsTotalCalculator(db, filter).transactionsBalance
}
