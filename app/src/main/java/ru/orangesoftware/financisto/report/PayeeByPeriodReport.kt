package ru.orangesoftware.financisto.report

import android.content.Context
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseHelper.TransactionColumns
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.graph.Report2DChart
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Payee
import java.util.Calendar

class PayeeByPeriodReport(
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
    override fun getFilterName(): String = if (filterIds.isNotEmpty()) {
        val payeeId: Long = filterIds[currentFilterOrder]
        val payee: Payee? = em.get(Payee::class.java, payeeId)
        payee?.title ?: context.getString(R.string.no_payee)
    } else {
        // no payee
        context.getString(R.string.no_payee)
    }

    override fun setFilterIds() {
        filterIds = mutableListOf()
        currentFilterOrder = 0;
        em.allPayeeList.forEach { filterIds.add(it.id) }
    }

    override fun setColumnFilter() {
        columnFilter = TransactionColumns.payee_id.name
    }

    override fun getNoFilterMessage(context: Context?): String? =
        context?.getString(R.string.report_no_payee)
}
