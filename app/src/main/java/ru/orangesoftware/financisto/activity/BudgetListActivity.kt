// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.BudgetListAdapter
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.datetime.PeriodType
import ru.orangesoftware.financisto.db.BudgetsTotalCalculator
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.DateTimeCriteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.RecurUtils
import ru.orangesoftware.financisto.utils.RecurUtils.RecurInterval
import ru.orangesoftware.financisto.utils.Utils

@Suppress("TooManyFunctions")
class BudgetListActivity : AbstractListActivity(R.layout.budget_list) {

    private val logger: Logger = DependenciesHolder().logger

    private lateinit var bFilter: ImageButton

    private var filter: WhereFilter = WhereFilter.empty()
    private var budgets: ArrayList<Budget> = ArrayList()
    private var totalCalculationJob: Job? = null

    override fun internalOnCreate(savedInstanceState: Bundle?) {
        super.internalOnCreate(savedInstanceState)

        val totalText = findViewById<TextView>(R.id.total)
        totalText.setOnClickListener { showTotals() }

        bFilter = findViewById(R.id.bFilter)
        bFilter.setOnClickListener {
            val intent = Intent(this@BudgetListActivity, DateFilterActivity::class.java)
            filter.toIntent(intent)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, FILTER_BUDGET_REQUEST)
        }

        if (filter.isEmpty) {
            filter = WhereFilter.fromSharedPreferences(getPreferences(0))
        }
        if (filter.isEmpty) {
            filter.put(DateTimeCriteria(PeriodType.THIS_MONTH))
        }

        budgets = db.getAllBudgets(filter)

        applyFilter()
        calculateTotals()
    }

    override fun onDestroy() {
        totalCalculationJob?.cancel()
        super.onDestroy()
    }

    private fun showTotals() {
        val intent = Intent(this, BudgetListTotalsDetailsActivity::class.java)
        filter.toIntent(intent)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, -1)
    }

    private fun saveFilter() {
        val preferences = getPreferences(0)
        filter.toSharedPreferences(preferences)
        applyFilter()
        recreateCursor()
    }

    private fun applyFilter() {
        FilterState.updateFilterColor(this, filter, bFilter)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILTER_BUDGET_REQUEST) {
            if (resultCode == RESULT_FIRST_USER) {
                filter.clear()
            } else if (resultCode == RESULT_OK && data != null) {
                val periodType = data.getStringExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TYPE)
                if (periodType != null) {
                    val p = PeriodType.valueOf(periodType)
                    if (PeriodType.CUSTOM == p) {
                        val periodFrom = data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_FROM, 0)
                        val periodTo = data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TO, 0)
                        filter.put(DateTimeCriteria(periodFrom, periodTo))
                    } else {
                        filter.put(DateTimeCriteria(p))
                    }
                }
            }
            saveFilter()
        }
        recreateCursor()
    }

    override fun createAdapter(cursor: Cursor?): ListAdapter =
        BudgetListAdapter(this, budgets)

    override fun createCursor(): Cursor? = null

    override fun recreateCursor() {
        budgets = db.getAllBudgets(filter)
        updateAdapter()
        calculateTotals()
    }

    private fun updateAdapter() {
        (adapter as? BudgetListAdapter)?.setBudgets(budgets)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun calculateTotals() {
        totalCalculationJob?.cancel()
        val totalText = findViewById<TextView>(R.id.total)
        val budgetsSnapshot = budgets.toList()
        totalCalculationJob = lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val c = BudgetsTotalCalculator(db, budgetsSnapshot)
                    c.updateBudgets(Dispatchers.Main)
                    ensureActive()
                    c.calculateTotalInHomeCurrency()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (ex: Exception) {
                logger.e(ex, "Unexpected error")
                Total.ZERO
            }
            val u = Utils(this@BudgetListActivity)
            u.setTotal(totalText, result)
            (adapter as? BudgetListAdapter)?.notifyDataSetChanged()
        }
    }

    override fun addItem() {
        val intent = Intent(this, BudgetActivity::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, NEW_BUDGET_REQUEST)
    }

    override fun deleteItem(v: View?, position: Int, id: Long) {
        val b = db.load(Budget::class.java, id) ?: return
        if (b.parentBudgetId > 0) {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_budget_recurring_select)
                .setPositiveButton(R.string.delete_budget_one_entry) { _, _ ->
                    db.deleteBudgetOneEntry(id)
                    recreateCursor()
                }
                .setNeutralButton(R.string.delete_budget_all_entries) { _, _ ->
                    db.deleteBudget(b.parentBudgetId)
                    recreateCursor()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            val recur = RecurUtils.createFromExtraString(b.recur)
            val confirmMessageResId = if (recur.interval == RecurInterval.NO_RECUR) {
                R.string.delete_budget_confirm
            } else {
                R.string.delete_budget_recurring_confirm
            }
            AlertDialog.Builder(this)
                .setMessage(confirmMessageResId)
                .setPositiveButton(R.string.yes) { _, _ ->
                    db.deleteBudget(id)
                    recreateCursor()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    public override fun editItem(v: View?, position: Int, id: Long) {
        val b = db.load(Budget::class.java, id) ?: return
        val recur = b.getRecur()
        if (recur.interval != RecurInterval.NO_RECUR) {
            val t = Toast.makeText(this, R.string.edit_recurring_budget, Toast.LENGTH_LONG)
            t.show()
        }
        val intent = Intent(this, BudgetActivity::class.java)
        intent.putExtra(BudgetActivity.BUDGET_ID_EXTRA, if (b.parentBudgetId > 0) b.parentBudgetId else id)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EDIT_BUDGET_REQUEST)
    }

    override fun viewItem(v: View?, position: Int, id: Long) {
        val b = db.load(Budget::class.java, id) ?: return
        val intent = Intent(this, BudgetBlotterActivity::class.java)
        Criteria.eq(BlotterFilter.BUDGET_ID, id.toString())
            .toIntent(b.title, intent)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, VIEW_BUDGET_REQUEST)
    }

    companion object {
        private const val NEW_BUDGET_REQUEST = 1
        private const val EDIT_BUDGET_REQUEST = 2
        private const val VIEW_BUDGET_REQUEST = 3
        private const val FILTER_BUDGET_REQUEST = 4
    }
}
