/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.activity

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.Spinner
import android.widget.SpinnerAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.GenericViewHolder
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.rates.ExchangeRate
import ru.orangesoftware.financisto.rates.ExchangeRateProvider
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils.formatRateDate
import java.text.DecimalFormat

@Suppress("TooManyFunctions")
class ExchangeRatesListActivity : AbstractListActivity(R.layout.exchange_rate_list) {

    private val nf = DecimalFormat("0.00000")

    private lateinit var fromCurrencySpinner: Spinner
    private lateinit var toCurrencySpinner: Spinner
    private var currencies: List<Currency> = emptyList()

    private var lastSelectedCurrencyId: Long = 0
    private var downloadJob: Job? = null

    override fun internalOnCreate(savedInstanceState: Bundle?) {
        super.internalOnCreate(savedInstanceState)
        currencies = db.getAllCurrenciesList("name")

        fromCurrencySpinner = findViewById(R.id.spinnerFromCurrency)
        fromCurrencySpinner.setPromptId(R.string.rate_from_currency)
        toCurrencySpinner = findViewById(R.id.spinnerToCurrency)
        toCurrencySpinner.setPromptId(R.string.rate_to_currency)

        if (currencies.isNotEmpty()) {
            toCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                    updateAdapter()
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {}
            }

            fromCurrencySpinner.adapter = createCurrencyAdapter(currencies)
            fromCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val filteredCurrencies = getCurrenciesButSelected(id)
                    if (filteredCurrencies.isNotEmpty()) {
                        val position = findSelectedCurrency(filteredCurrencies, lastSelectedCurrencyId)
                        toCurrencySpinner.adapter = createCurrencyAdapter(filteredCurrencies)
                        toCurrencySpinner.setSelection(position)
                    }
                    lastSelectedCurrencyId = id
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {}
            }
            fromCurrencySpinner.setSelection(findDefaultCurrency())

            val bFlip = findViewById<ImageButton>(R.id.bFlip)
            bFlip.setOnClickListener { flipCurrencies() }

            val bRefresh = findViewById<ImageButton>(R.id.bRefresh)
            bRefresh.setOnClickListener { refreshAllRates() }
        }
    }

    private fun createCurrencyAdapter(currencies: List<Currency>): SpinnerAdapter {
        val a = object : ArrayAdapter<Currency>(this, R.layout.themed_spinner_item, currencies) {
            override fun getItemId(position: Int): Long {
                return getItem(position)?.id ?: 0L
            }
        }
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun getCurrenciesButSelected(id: Long): List<Currency> =
        currencies.filter { it.id != id }

    private fun findSelectedCurrency(currencies: List<Currency>, id: Long): Int {
        val index = currencies.indexOfFirst { it.id == id }
        return if (index >= 0) index else 0
    }

    private fun findDefaultCurrency(): Int {
        val index = currencies.indexOfFirst { it.isDefault }
        return if (index >= 0) index else 0
    }

    private fun flipCurrencies() {
        val toCurrency = toCurrencySpinner.selectedItem as? Currency
        if (toCurrency != null) {
            fromCurrencySpinner.setSelection(findSelectedCurrency(currencies, toCurrency.id))
        }
    }

    private fun updateAdapter() {
        val fromCurrency = fromCurrencySpinner.selectedItem as? Currency
        val toCurrency = toCurrencySpinner.selectedItem as? Currency
        if (fromCurrency != null && toCurrency != null) {
            val rates = db.findRates(fromCurrency, toCurrency)
            val listAdapter: ListAdapter = ExchangeRateListAdapter(this, rates)
            setListAdapter(listAdapter)
        }
    }

    override fun addItem() {
        val fromCurrencyId = fromCurrencySpinner.selectedItemId
        val toCurrencyId = toCurrencySpinner.selectedItemId
        if (fromCurrencyId > 0 && toCurrencyId > 0) {
            val intent = Intent(this, ExchangeRateActivity::class.java).apply {
                putExtra(ExchangeRateActivity.FROM_CURRENCY_ID, fromCurrencyId)
                putExtra(ExchangeRateActivity.TO_CURRENCY_ID, toCurrencyId)
            }
            startActivityForResult(intent, ADD_RATE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            updateAdapter()
        }
    }

    override fun createCursor(): Cursor? = null

    override fun createAdapter(cursor: Cursor?): ListAdapter? = null

    override fun deleteItem(v: View?, position: Int, id: Long) {
        val rate = getListAdapter()?.getItem(position) as? ExchangeRate ?: return
        db.deleteRate(rate)
        updateAdapter()
    }

    override fun editItem(v: View?, position: Int, id: Long) {
        val rate = getListAdapter()?.getItem(position) as? ExchangeRate ?: return
        editRate(rate)
    }

    override fun viewItem(v: View?, position: Int, id: Long) {
        editItem(v, position, id)
    }

    private fun editRate(rate: ExchangeRate) {
        val intent = Intent(this, ExchangeRateActivity::class.java).apply {
            putExtra(ExchangeRateActivity.FROM_CURRENCY_ID, rate.fromCurrencyId)
            putExtra(ExchangeRateActivity.TO_CURRENCY_ID, rate.toCurrencyId)
            putExtra(ExchangeRateActivity.RATE_DATE, rate.date)
        }
        startActivityForResult(intent, EDIT_RATE)
    }

    private fun refreshAllRates() {
        val currenciesSnapshot = currencies.toList()
        val message = getString(R.string.downloading_rates, asString(currenciesSnapshot))

        downloadJob?.cancel()
        val progressDialog = ProgressDialog.show(
            this,
            null,
            message,
            true,
            true,
        ) {
            downloadJob?.cancel()
        }

        downloadJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val rates = getProvider().getRates(currenciesSnapshot)
                    ensureActive()
                    db.saveDownloadedRates(rates)
                    rates
                }
                showResult(result)
                updateAdapter()
            } finally {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                }
            }
        }
    }

    private fun asString(currencies: List<Currency>): String =
        currencies.joinToString(", ") { it.name }

    private fun showResult(result: List<ExchangeRate>) {
        val sb = StringBuilder()
        for (rate in result) {
            val fromCurrency = CurrencyCache.getCurrency(db, rate.fromCurrencyId)
            val toCurrency = CurrencyCache.getCurrency(db, rate.toCurrencyId)
            sb.append(fromCurrency.name).append(" -> ").append(toCurrency.name)
            if (rate.isOk) {
                sb.append(" = ").append(nf.format(rate.rate))
            } else {
                sb.append(" ! ").append(rate.errorMessage)
            }
            sb.append(String.format("%n%n"))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.downloading_rates_result)
            .setMessage(sb.toString())
            .setNeutralButton(R.string.ok, null)
            .create()
            .show()
    }

    private fun getProvider(): ExchangeRateProvider =
        MyPreferences.createExchangeRatesProvider(this)

    private inner class ExchangeRateListAdapter(
        private val context: Context,
        private val rates: List<ExchangeRate>,
    ) : BaseAdapter() {

        private val itemInflater: LayoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        override fun getCount(): Int = rates.size

        override fun getItem(i: Int): ExchangeRate = rates[i]

        override fun getItemId(i: Int): Long = i.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val v: GenericViewHolder
            if (convertView == null) {
                view = itemInflater.inflate(R.layout.generic_list_item, parent, false)
                v = GenericViewHolder.createAndTag(view)
            } else {
                view = convertView
                v = view.tag as GenericViewHolder
            }
            val rate = getItem(position)
            v.lineView?.text = formatRateDate(context, rate.date)
            v.amountView?.text = nf.format(rate.rate)
            return view
        }
    }

    companion object {
        private const val ADD_RATE = 1
        private const val EDIT_RATE = 1
    }
}
