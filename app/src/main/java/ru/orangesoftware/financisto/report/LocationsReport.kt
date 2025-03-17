package ru.orangesoftware.financisto.report

import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_LOCATIONS
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.graph.GraphStyle
import ru.orangesoftware.financisto.model.Currency

class LocationsReport(
	currency: Currency,
	skipTransfers: Boolean,
	screenDensity: Float,
) : Report(ReportType.BY_LOCATION, currency, skipTransfers, screenDensity) {

	override fun getReport(db: DatabaseAdapter?, filter: WhereFilter?): ReportData {
		cleanupFilter(filter)
		return queryReport(db, V_REPORT_LOCATIONS, filter)
	}

	override fun alterName(id: Long, name: String?): String =
//		if (id == 0L) context.getString(R.string.no_fix) else name ?: ""
		if (id == 0L) "Unknown location" else name ?: ""

	override fun getCriteriaForId(db: DatabaseAdapter, id: Long): Criteria =
		Criteria.eq(BlotterFilter.LOCATION_ID, id.toString())
}
