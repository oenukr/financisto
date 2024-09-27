package ru.orangesoftware.financisto.report

import android.content.Context
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_PAYEES
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency

class PayeesReport(
	context: Context,
	currency: Currency,
) : Report(ReportType.BY_PAYEE, context, currency) {
	override fun getReport(db: DatabaseAdapter?, filter: WhereFilter?): ReportData {
		cleanupFilter(filter)
		return queryReport(db, V_REPORT_PAYEES, filter)
	}

	override fun getCriteriaForId(db: DatabaseAdapter, id: Long): Criteria =
		Criteria.eq(BlotterFilter.PAYEE_ID, id.toString())
}
