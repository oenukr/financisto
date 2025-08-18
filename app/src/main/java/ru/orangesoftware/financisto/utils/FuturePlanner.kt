package ru.orangesoftware.financisto.utils

import android.database.Cursor

import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.TransactionsTotalCalculator
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.model.TransactionInfo
import java.util.Date

class FuturePlanner(
    db: DatabaseAdapter,
    filter: WhereFilter,
    now: Date,
) : AbstractPlanner(db.context, db, filter, now) {

    override fun getRegularTransactions(): Cursor = db.getBlotter(WhereFilter.copyOf(filter))

    override fun prepareScheduledTransaction(
        scheduledTransaction: TransactionInfo,
    ): TransactionInfo = scheduledTransaction

    override fun includeScheduledTransaction(transaction: TransactionInfo): Boolean = true

    override fun includeScheduledSplitTransaction(split: TransactionInfo): Boolean = false

    override fun calculateTotals(transactions: List<TransactionInfo>): Array<Total> =
        arrayOf(
            TransactionsTotalCalculator(db, filter).calculateTotalFromListInHomeCurrency(db, transactions)
//            TransactionsTotalCalculator.calculateTotalFromListInHomeCurrency(
//                db,
//                transactions
//            )
        )
}
