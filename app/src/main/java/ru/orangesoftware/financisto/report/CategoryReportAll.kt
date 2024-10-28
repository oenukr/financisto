package ru.orangesoftware.financisto.report

import android.content.Context
import ru.orangesoftware.financisto.activity.BlotterActivity
import ru.orangesoftware.financisto.activity.SplitsBlotterActivity
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_CATEGORY
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency

class CategoryReportAll(
	context: Context,
	currency: Currency,
) : Report(ReportType.BY_CATEGORY, context, currency) {

	override fun getReport(db: DatabaseAdapter?, filter: WhereFilter?): ReportData {
		cleanupFilter(filter)
		return queryReport(db, V_REPORT_CATEGORY, filter)
	}

	override fun getCriteriaForId(db: DatabaseAdapter, id: Long): Criteria {
		val c: Category = db.getCategoryWithParent(id)
		return Criteria.btw(BlotterFilter.CATEGORY_LEFT, c.left.toString(), c.right.toString())
	}

    override fun shouldDisplayTotal(): Boolean = false

    override fun getBlotterActivityClass(): Class<out BlotterActivity> =
        SplitsBlotterActivity::class.java
}
