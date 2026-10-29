// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.rates.ExchangeRate
import ru.orangesoftware.financisto.utils.Utils

abstract class AbstractTotalsDetailsActivity protected constructor(
    private val titleNodeResId: Int,
) : AbstractActivity() {

    private lateinit var layout: LinearLayout
    private lateinit var calculatingNode: View
    private lateinit var u: Utils
    protected var shouldShowHomeCurrencyTotal: Boolean = true
    private var calculateTotalsJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        setContentView(R.layout.totals_details)

        u = Utils(this)
        layout = findViewById(R.id.list)
        calculatingNode = activityLayout.addTitleNodeNoDivider(layout, R.string.calculating)

        val bOk = findViewById<Button>(R.id.bOK)
        bOk.setOnClickListener { finish() }

        internalOnCreate()
        calculateTotals()
    }

    protected open fun internalOnCreate() {}

    private fun calculateTotals() {
        calculateTotalsJob?.cancel()
        calculateTotalsJob = lifecycleScope.launch {
            val totals = withContext(Dispatchers.IO) {
                prepareInBackground()
                ensureActive()
                val totalsArray = getTotals()
                val totalInHomeCurrency = getTotalInHomeCurrency()
                val homeCurrency = totalInHomeCurrency.currency
                val rates = db.latestRates
                val result = totalsArray.map { total ->
                    val rate = rates.getRate(total.currency, homeCurrency)
                    TotalInfo(total, rate)
                }.sortedBy { it.total.currency.name }
                ensureActive()
                TotalsInfo(result, totalInHomeCurrency)
            }
            calculatingNode.visibility = View.GONE
            for (total in totals.totals) {
                val title = getString(titleNodeResId, total.total.currency.name)
                addAmountNode(total.total, title)
            }
            if (shouldShowHomeCurrencyTotal) {
                addAmountNode(totals.totalInHomeCurrency, getString(R.string.home_currency_total))
            }
        }
    }

    private fun addAmountNode(total: Total, title: String) {
        activityLayout.addTitleNodeNoDivider(layout, title)
        if (total.isError) {
            addAmountAndErrorNode(total)
        } else {
            addSingleAmountNode(total)
        }
    }

    private fun addAmountAndErrorNode(total: Total) {
        val data = activityLayout.addInfoNode(layout, -1, R.string.not_available, "")
        val dr: Drawable? = ContextCompat.getDrawable(baseContext, R.drawable.total_error)
        dr?.setBounds(0, 0, dr.intrinsicWidth, dr.intrinsicHeight)
        if (total.currency == Currency.EMPTY) {
            data.setText(R.string.currency_make_default_warning)
        } else {
            data.text = total.getError(this)
        }
        data.setError("Error!", dr)
    }

    private fun addSingleAmountNode(total: Total) {
        val label = activityLayout.addInfoNodeSingle(layout, -1, "")
        u.setAmountText(label, total)
    }

    override fun onClick(v: View, id: Int) {}

    override fun onDestroy() {
        calculateTotalsJob?.cancel()
        super.onDestroy()
    }

    protected abstract fun getTotalInHomeCurrency(): Total

    protected abstract fun getTotals(): Array<Total>

    @Suppress("RedundantSuspendModifier")
    protected open suspend fun prepareInBackground() {}

    private class TotalInfo(
        val total: Total,
        val rate: ExchangeRate,
    )

    private class TotalsInfo(
        val totals: List<TotalInfo>,
        val totalInHomeCurrency: Total,
    )
}
