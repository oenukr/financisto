// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.blotter

import android.content.Context
import android.widget.TextView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.TransactionsTotalCalculator
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total

class BlotterTotalCalculationTask(
    context: Context?,
    private val db: DatabaseAdapter,
    private val filter: WhereFilter,
    totalText: TextView?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TotalCalculationTask(context, totalText, ioDispatcher) {

    override fun getTotalInHomeCurrency(): Total =
        TransactionsTotalCalculator(db, filter).blotterBalanceInHomeCurrency

    override fun getTotals(): Array<Total> =
        TransactionsTotalCalculator(db, filter).transactionsBalance
}
