package ru.orangesoftware.financisto.activity

import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListAdapter
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.MyEntity
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Total

class BudgetBlotterActivity : BlotterActivity() {
	
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
				val t0 = System.currentTimeMillis()
				try {
					try {
						val budgetId = blotterFilter.getBudgetId()
						val b = db.load(Budget::class.java, budgetId)
						val total = Total(b.getBudgetCurrency())
						total.balance = db.fetchBudgetBalance(categories, projects, b)
						return total
					} finally {
						val t1 = System.currentTimeMillis()
						Log.d("BUDGET TOTALS", "${(t1-t0)}ms")
					}
				} catch (ex: Exception) {
					Log.e("BudgetTotals", "Unexpected error", ex)
					return Total.ZERO
				}
			}

			override fun getTotals(): Array<Total> = emptyArray()
		}
    }
}
