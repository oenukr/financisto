package ru.orangesoftware.financisto.report

import android.content.Context
import ru.orangesoftware.financisto.activity.BlotterActivity
import ru.orangesoftware.financisto.activity.SplitsBlotterActivity
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_PROJECTS
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Currency

class ProjectsReport(
	context: Context,
	currency: Currency,
) : Report(ReportType.BY_PROJECT, context, currency) {
	override fun getReport(db: DatabaseAdapter?, filter: WhereFilter?): ReportData {
		cleanupFilter(filter)
		return queryReport(db, V_REPORT_PROJECTS, filter)
	}

	override fun getCriteriaForId(db: DatabaseAdapter, id: Long): Criteria =
		Criteria.eq(BlotterFilter.PROJECT_ID, id.toString())

	override fun getBlotterActivityClass(): Class<out BlotterActivity> =
		SplitsBlotterActivity::class.java
}
