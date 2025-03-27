package ru.orangesoftware.financisto.activity

import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.ListAdapter
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.MyEntity
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.Logger
import kotlin.time.measureTimedValue

class BudgetBlotterActivity : BlotterActivity() {

	private val logger: Logger = DependenciesHolder().logger
	
	private val categories: Map<Long, Category> by lazy {
        MyEntity.asMap(db.getCategoriesList(true))
    }
	private val projects: Map<Long, Project> by lazy {
        MyEntity.asMap(db.getActiveProjectsList(true))
    }
	
	override fun internalOnCreate(savedInstanceState: Bundle?) {
		super.internalOnCreate(savedInstanceState)
		bFilter.setVisibility(View.GONE)
	}
	
	override fun createCursor(): Cursor {
		val budgetId = blotterFilter.getBudgetId()
		return getBlotterForBudget(budgetId)
	}

	override fun createAdapter(cursor: Cursor): ListAdapter {
		return TransactionsListAdapter(this, db, cursor)
	}
	
	private fun getBlotterForBudget(budgetId: Long): Cursor {
		val b = db.load(Budget::class.java, budgetId)
		val where = Budget.createWhere(b, categories, projects)
		return db.getBlotterWithSplits(where)
	}

    override fun createTotalCalculationTask(): TotalCalculationTask {
        return object : TotalCalculationTask(this, totalText) {
			override fun getTotalInHomeCurrency(): Total {
				try {
					val (total, duration) = measureTimedValue {
						try {
							val budgetId = blotterFilter.getBudgetId()
							val b = db.load(Budget::class.java, budgetId)
							val total = Total(b.getBudgetCurrency())
							total.balance = db.fetchBudgetBalance(categories, projects, b)
							total
						} finally { }
					}
					logger.d("${duration.inWholeMilliseconds}ms")
					return total
				} catch (ex: Exception) {
					logger.e(ex, "Unexpected error")
					return Total.ZERO
				}
			}

			override fun getTotals(): Array<Total> = emptyArray()
		}
    }
}
