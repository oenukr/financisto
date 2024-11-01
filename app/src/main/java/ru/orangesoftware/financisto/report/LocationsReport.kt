package ru.orangesoftware.financisto.report

import android.content.Context
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_LOCATIONS
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency

class LocationsReport(
	context: Context,
	currency: Currency,
) : Report(ReportType.BY_LOCATION, context, currency) {

	override fun getReport(db: DatabaseAdapter?, filter: WhereFilter?): ReportData {
		cleanupFilter(filter)
		return queryReport(db, V_REPORT_LOCATIONS, filter)
	}

	override fun alterName(id: Long, name: String?): String =
		if (id == 0L) context.getString(R.string.no_fix) else name ?: ""

	override fun getCriteriaForId(db: DatabaseAdapter, id: Long): Criteria =
		Criteria.eq(BlotterFilter.LOCATION_ID, id.toString())
}
