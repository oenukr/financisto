package ru.orangesoftware.financisto.report

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import ru.orangesoftware.financisto.datetime.Period
import ru.orangesoftware.financisto.datetime.PeriodType
import ru.orangesoftware.financisto.datetime.PeriodType.LAST_MONTH
import ru.orangesoftware.financisto.datetime.PeriodType.LAST_WEEK
import ru.orangesoftware.financisto.datetime.PeriodType.THIS_AND_LAST_MONTH
import ru.orangesoftware.financisto.datetime.PeriodType.THIS_AND_LAST_WEEK
import ru.orangesoftware.financisto.datetime.PeriodType.THIS_MONTH
import ru.orangesoftware.financisto.datetime.PeriodType.THIS_WEEK
import ru.orangesoftware.financisto.datetime.PeriodType.TODAY
import ru.orangesoftware.financisto.datetime.PeriodType.YESTERDAY
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.ReportColumns
import ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_PERIOD
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.DateTimeCriteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.graph.GraphUnit
import ru.orangesoftware.financisto.model.Currency

class PeriodReport(
    currency: Currency,
    skipTransfers: Boolean,
    screenDensity: Float,
) : Report(ReportType.BY_PERIOD, currency, skipTransfers, screenDensity) {

    private val periodTypes = arrayOf<PeriodType>(
        TODAY,
        YESTERDAY,
        THIS_WEEK,
        LAST_WEEK,
        THIS_AND_LAST_WEEK,
        THIS_MONTH,
        LAST_MONTH,
        THIS_AND_LAST_MONTH,
    )
	private val periods = periodTypes.map { it.calculatePeriod() }.toTypedArray()

    private lateinit var currentPeriod: Period

    override fun getReport(db: DatabaseAdapter, filter: WhereFilter): ReportData {
        val newFilter = WhereFilter.empty()
        val criteria = filter.get(ReportColumns.FROM_ACCOUNT_CURRENCY_ID)
        if (criteria != null) {
            newFilter.put(criteria)
        }
        filterTransfers(newFilter)
        val units = mutableListOf<GraphUnit>()

        periods.forEach {
            currentPeriod = it
            newFilter.put(
                Criteria.btw(
                    ReportColumns.DATETIME,
                    it.start.toString(),
                    it.end.toString()
                )
            )
            val query: SupportSQLiteQuery = SupportSQLiteQueryBuilder
                .builder(V_REPORT_PERIOD)
                .columns(ReportColumns.NORMAL_PROJECTION)
                .selection(newFilter.selection, newFilter.selectionArgs)
                .create()
            val cursor = db.db().query(query)
            val u = getUnitsFromCursor(db, cursor)
            if (!u.isEmpty() && u[0].size() > 0) {
                units.add(u[0])
            }
        }
        val total = calculateTotal(units)
        return ReportData(units, total)
    }

    override fun getId(c: Cursor): Long = currentPeriod.type.ordinal.toLong()

    // TODO: localize
    override fun alterName(id: Long, name: String?): String = currentPeriod.type.defaultTitle//currentPeriod.type.titleId.toString()
//    protected String alterName(long id, String name) {
//        return context.getString(currentPeriod.getType().titleId);
//    }

    override fun getCriteriaForId(db: DatabaseAdapter, id: Long): Criteria? =
        periods
            .find { it.type.ordinal.toLong() == id }
            ?.let { DateTimeCriteria(it) }

    override fun shouldDisplayTotal(): Boolean = false
}
