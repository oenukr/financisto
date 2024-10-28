package ru.orangesoftware.financisto.report

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.DatabaseHelper.CategoryColumns
import ru.orangesoftware.financisto.db.DatabaseHelper.TransactionColumns
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.graph.Report2DChart
import ru.orangesoftware.financisto.graph.Report2DPoint
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.ReportDataByPeriod
import ru.orangesoftware.financisto.utils.MyPreferences
import java.util.Calendar

/**
 * 2D Chart Report to display monthly results by Categories.
 *
 * @author Abdsandryk
 */
class CategoryByPeriodReport(
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
    override fun getFilterName(): String =
        if (filterIds.isNotEmpty()) {
            val categoryId: Long = filterIds[currentFilterOrder]
            val category: Category? = em.getCategory(categoryId)
            category?.getTitle() ?: context.getString(R.string.no_category)
        } else {
            // no category
            context.getString(R.string.no_category)
        }

    override fun setFilterIds() {
        val includeSubCategories: Boolean = MyPreferences.includeSubCategoriesInReport(context)
        val includeNoCategory: Boolean = MyPreferences.includeNoFilterInReport(context)
        filterIds = mutableListOf()
        currentFilterOrder = 0
        val categories: List<Category> = em.getAllCategoriesList(includeNoCategory)
        if (categories.isNotEmpty()) {
            categories.forEach {
                if (includeSubCategories) {
                    filterIds.add(it.id)
                } else {
                    // do not include sub categories
                    if (it.level == 1) {
                        // filter root categories only
                        filterIds.add(it.id)
                    }
                }
            }
        }
    }

    override fun setColumnFilter() {
        columnFilter = TransactionColumns.category_id.name
    }

    /**
     * Request data and fill data objects (list of points, max, min, etc.)
     */
    override fun build() {
    val addSubs: Boolean = MyPreferences.addSubCategoriesToSum(context)
    if (addSubs) {
        val db: SQLiteDatabase = em.db()
        val categoryId: Long = filterIds[currentFilterOrder]
        val parent: Category? = em.getCategory(categoryId)
        val where: String = "${CategoryColumns.left} BETWEEN ? AND ?"
        val pars: Array<String> = arrayOf(parent?.left.toString(), parent?.right.toString())

        val categories = mutableListOf<Int>()
        db.query(
            DatabaseHelper.CATEGORY_TABLE,
            arrayOf(CategoryColumns._id.name),
            where,
            pars,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                categories.add(cursor.getInt(0))
            }
        }
        categories.add(filterIds[currentFilterOrder].toInt())

        data = ReportDataByPeriod(
            context,
            startPeriod,
            periodLength,
            currency,
            columnFilter,
            categories.toIntArray(),
            em
        )
    } else {
        // only root category
        data = ReportDataByPeriod(
            context,
            startPeriod,
            periodLength,
            currency,
            columnFilter,
            filterIds[currentFilterOrder].toInt(),
            em
        )
    }

    points = data.periodValues.map { Report2DPoint(it) }
}

    override fun getNoFilterMessage(context: Context): String =
        context.getString(R.string.report_no_category)
}
