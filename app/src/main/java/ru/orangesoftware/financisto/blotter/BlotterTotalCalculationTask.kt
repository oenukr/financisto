package ru.orangesoftware.financisto.blotter

import android.content.Context
import android.widget.TextView

import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.TransactionsTotalCalculator
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total

class BlotterTotalCalculationTask(
    context: Context,
    private val db: DatabaseAdapter,
    private val filter: WhereFilter,
    totalText: TextView?
) : TotalCalculationTask(context, totalText) {
    override fun getTotalInHomeCurrency(): Total =
        TransactionsTotalCalculator(db, filter).blotterBalanceInHomeCurrency

    override fun getTotals(): Array<Total> =
        TransactionsTotalCalculator(db, filter).transactionsBalance
}
