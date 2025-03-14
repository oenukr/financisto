package ru.orangesoftware.financisto.blotter

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.widget.TextView
import android.widget.Toast

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.Utils

abstract class TotalCalculationTask(
	private val context: Context,
	private val totalText: TextView?,
) : AsyncTask<Any, Total, Total>() {

	@Volatile
	private var isRunning: Boolean = true

	@Deprecated("Deprecated in Java")
	override fun doInBackground(vararg params: Any?): Total {
		try {
		    return getTotalInHomeCurrency()
		} catch (ex: Exception) {
			Log.e("TotalBalance", "Unexpected error", ex)
			return Total.ZERO
		}
	}

	abstract fun getTotalInHomeCurrency(): Total
	abstract fun getTotals(): Array<Total>

	@Deprecated("Deprecated in Java")
	override fun onPostExecute(result: Total?) {
		if (isRunning) {
			if (result?.currency == Currency.EMPTY) {
				Toast.makeText(context, R.string.currency_make_default_warning, Toast.LENGTH_LONG).show()
			}
			val u = Utils(context)
			u.setTotal(totalText, result)
		}
	}

	fun stop() {
		isRunning = false
	}
}
