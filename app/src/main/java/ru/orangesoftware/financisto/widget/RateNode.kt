/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.widget

import android.app.ProgressDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.ActivityLayout
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.rates.ExchangeRateProvider
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils
import java.text.DecimalFormat
import kotlin.math.abs

class RateNode(
    private val owner: RateNodeOwner,
    private val x: ActivityLayout,
    private val layout: LinearLayout,
) {

    private val nf = DecimalFormat("0.00000")

    @JvmField
    var rateInfoNode: View? = null

    private lateinit var rateInfo: TextView
    private lateinit var rateView: EditText

    private lateinit var bCalc: ImageButton
    private lateinit var bDownload: ImageButton

    private var downloadJob: Job? = null

    private val rateWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            owner.onRateChanged()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    init {
        createUI()
    }

    @Suppress("SwallowedException")
    private fun createUI() {
        val node = x.addRateNode(layout)
        rateInfoNode = node
        rateView = node.findViewById(R.id.rate)
        rateView.addTextChangedListener(rateWatcher)
        rateView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                rateView.selectAll()
            }
        }
        rateInfo = node.findViewById(R.id.data)
        bCalc = node.findViewById(R.id.rateCalculator)
        bCalc.setOnClickListener {
            val activity = owner.getActivity()
            if (activity is FragmentActivity) {
                val input = CalculatorInput_.builder().amount(getRate().toString()).build()
                input.setListener(
                    object : AmountListener {
                        override fun onAmountChanged(amount: String) {
                            try {
                                setRate(amount.toFloat().toDouble())
                                updateRateInfo()
                                owner.onRateChanged()
                            } catch (ignored: NumberFormatException) {
                            }
                        }
                    },
                )
                input.show(activity.supportFragmentManager, "calculator")
            }
        }
        bDownload = node.findViewById(R.id.rateDownload)
        bDownload.setOnClickListener { downloadRate() }
    }

    fun disableAll() {
        rateView.isEnabled = false
        bCalc.isEnabled = false
        bDownload.isEnabled = false
    }

    fun enableAll() {
        rateView.isEnabled = true
        bCalc.isEnabled = true
        bDownload.isEnabled = true
    }

    @Suppress("SwallowedException")
    fun getRate(): Float {
        return try {
            val rateText = Utils.text(rateView)
            if (rateText != null) {
                rateText.replace(',', '.').toFloat()
            } else {
                0f
            }
        } catch (ex: NumberFormatException) {
            0f
        }
    }

    fun setRate(r: Double) {
        rateView.removeTextChangedListener(rateWatcher)
        rateView.setText(nf.format(abs(r)))
        rateView.addTextChangedListener(rateWatcher)
    }

    fun updateRateInfo() {
        val r = getRate().toDouble()
        val sb = StringBuilder()
        val currencyFrom: Currency? = owner.getCurrencyFrom()
        val currencyTo: Currency? = owner.getCurrencyTo()
        if (currencyFrom != null && currencyTo != null) {
            sb.append("1").append(currencyFrom.name).append("=").append(nf.format(r)).append(currencyTo.name).append(", ")
            sb.append("1").append(currencyTo.name).append("=").append(nf.format(1.0 / r)).append(currencyFrom.name)
        }
        rateInfo.text = sb.toString()
    }

    private fun downloadRate() {
        val activity = owner.getActivity()
        val fromCurrency: Currency? = owner.getCurrencyFrom()
        val toCurrency: Currency? = owner.getCurrencyTo()
        val message = activity.getString(R.string.downloading_rate, fromCurrency, toCurrency)

        downloadJob?.cancel()
        val scope = (activity as? LifecycleOwner)?.lifecycleScope
            ?: DependenciesHolder().applicationScope

        val progressDialog = ProgressDialog.show(
            activity,
            null,
            message,
            true,
            true,
        ) {
            downloadJob?.cancel()
        }

        owner.onBeforeRateDownload()
        downloadJob = scope.launch {
            try {
                val result = if (fromCurrency != null && toCurrency != null) {
                    withContext(Dispatchers.IO) {
                        val rate = getProvider().getRate(fromCurrency, toCurrency)
                        ensureActive()
                        rate
                    }
                } else {
                    null
                }
                if (result != null) {
                    if (result.isOk) {
                        setRate(result.rate)
                        owner.onSuccessfulRateDownload()
                    } else {
                        Toast.makeText(activity, result.errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    progressDialog.dismiss()
                }
                owner.onAfterRateDownload()
            }
        }
    }

    private fun getProvider(): ExchangeRateProvider =
        MyPreferences.createExchangeRatesProvider(owner.getActivity())

    companion object {
        const val EDIT_RATE: Int = 112
    }
}
