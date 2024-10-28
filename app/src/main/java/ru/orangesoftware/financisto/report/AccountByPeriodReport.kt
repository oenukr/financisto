package ru.orangesoftware.financisto.report

import android.content.Context
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseHelper.TransactionColumns
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.graph.Report2DChart
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import java.util.Calendar

/**
 * 2D Chart Report to display monthly account results.
 *
 * @author Abdsandryk
 */
class AccountByPeriodReport(
    context: Context,
    em: MyEntityManager,
    startPeriod: Calendar,
    periodLength: Int,
    currency: Currency,
) : Report2DChart(
    context,
    em,
    startPeriod,
    periodLength,
    currency,
) {

    /* (non-Javadoc)
     * @see ru.orangesoftware.financisto.graph.ReportGraphic2D#getFilterName()
     */
    override fun getFilterName(): String = if (filterIds.isNotEmpty()) {
        val accountId: Long = filterIds[currentFilterOrder]
        val a: Account? = em.getAccount(accountId)
        a?.title ?: context.getString(R.string.no_account)
    } else {
        // no category
        context.getString(R.string.no_account)
    }

    /* (non-Javadoc)
     * @see ru.orangesoftware.financisto.graph.ReportGraphic2D#setFilterIds()
     */
    override fun setFilterIds() {
        filterIds = mutableListOf()
        currentFilterOrder = 0
        val accounts: List<Account> = em.getAllAccountsList()
        if (accounts.isNotEmpty()) {
            accounts.forEach {
                filterIds.add(it.id)
            }
        }
    }

    override fun setColumnFilter() {
        columnFilter = TransactionColumns.from_account_id.name
    }

    override fun getNoFilterMessage(context: Context): String =
        context.getString(R.string.report_no_account)
}
