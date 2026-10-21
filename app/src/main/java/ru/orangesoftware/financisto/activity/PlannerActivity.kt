// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.ScheduledListAdapter
import ru.orangesoftware.financisto.datetime.PeriodType
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.filter.DateTimeCriteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.Utils
import java.util.Calendar

@Suppress("TooManyFunctions")
class PlannerActivity : AbstractListActivity(R.layout.planner) {

    private val viewModel: PlannerViewModel by viewModel()

    private lateinit var totalText: TextView
    private lateinit var filterText: TextView
    private var bFilter: ImageButton? = null

    private var filter: WhereFilter = WhereFilter.empty()

    override fun internalOnCreate(savedInstanceState: Bundle?) {
        totalText = findViewById(R.id.total)
        filterText = findViewById(R.id.period)
        bFilter = findViewById(R.id.bFilter)
        bFilter?.setOnClickListener { showFilter() }

        loadFilter()
        setupFilter()
        updateFilterButtonColor()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: PlannerUiState) {
        when (state) {
            is PlannerUiState.Loading -> Unit
            is PlannerUiState.Success -> {
                val adapter = ScheduledListAdapter(this, state.transactions)
                setListAdapter(adapter)
                setTotals(state.totals)
                updateFilterText(state.filter)
            }
            is PlannerUiState.Empty -> {
                val adapter = ScheduledListAdapter(this, emptyList())
                setListAdapter(adapter)
                setTotals(state.totals)
                updateFilterText(state.filter)
            }
            is PlannerUiState.Error -> {
                Toast.makeText(this, state.message ?: getString(R.string.error), Toast.LENGTH_SHORT).show()
                viewModel.onErrorShown()
            }
        }
    }

    private fun loadFilter() {
        val preferences = getPreferences(Context.MODE_PRIVATE)
        filter = WhereFilter.fromSharedPreferences(preferences)
        applyDateTimeCriteria(filter.dateTime)
    }

    private fun setupFilter() {
        if (filter.isEmpty) {
            applyDateTimeCriteria(null)
        }
    }

    private fun applyDateTimeCriteria(criteria: DateTimeCriteria?) {
        var c = criteria ?: run {
            val date = Calendar.getInstance()
            date.add(Calendar.MONTH, 1)
            DateTimeCriteria(PeriodType.THIS_MONTH)
        }
        val now = System.currentTimeMillis()
        if (now > c.longValue1) {
            val period = c.period
            period.start = now
            c = DateTimeCriteria(period)
        }
        filter.put(c)
    }

    private fun showFilter() {
        val intent = Intent(this, DateFilterActivity::class.java).apply {
            putExtra(DateFilterActivity.EXTRA_FILTER_DONT_SHOW_NO_FILTER, true)
            putExtra(DateFilterActivity.EXTRA_FILTER_SHOW_PLANNER, true)
        }
        filter.toIntent(intent)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_FILTER)
    }

    private fun saveFilter() {
        val preferences = getPreferences(Context.MODE_PRIVATE)
        filter.toSharedPreferences(preferences)
    }

    private fun updateFilterButtonColor() {
        bFilter?.let {
            FilterState.updateFilterColor(this, filter, it)
        }
    }

    override fun createCursor(): Cursor? {
        retrieveData()
        return null
    }

    override fun createAdapter(cursor: Cursor?): ListAdapter? = null

    override fun deleteItem(v: View?, position: Int, id: Long) {}

    override fun editItem(v: View?, position: Int, id: Long) {}

    override fun viewItem(v: View?, position: Int, id: Long) {}

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FILTER && resultCode == RESULT_OK && data != null) {
            val c = WhereFilter.dateTimeFromIntent(data)
            applyDateTimeCriteria(c)
            saveFilter()
            updateFilterButtonColor()
            retrieveData()
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun retrieveData() {
        viewModel.loadPlannedTransactions(filter)
    }

    private fun updateFilterText(filter: WhereFilter) {
        val c = filter.get(DatabaseHelper.ReportColumns.DATETIME)
        if (c != null) {
            val flags = DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_TIME or
                DateUtils.FORMAT_ABBREV_MONTH
            filterText.text = DateUtils.formatDateRange(this, c.longValue1, c.longValue2, flags)
        } else {
            filterText.setText(R.string.no_filter)
        }
    }

    private fun setTotals(totals: Array<Total>) {
        val u = Utils(this)
        u.setTotal(totalText, totals.firstOrNull() ?: Total.ZERO)
    }

    companion object {
        private const val REQUEST_FILTER = 1
    }
}
