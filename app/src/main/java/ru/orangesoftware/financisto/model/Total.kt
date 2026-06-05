package ru.orangesoftware.financisto.model

import android.content.Context
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.Utils

data class Total @JvmOverloads constructor(
    @JvmField val currency: Currency,
    @JvmField val showAmount: Boolean = false,
    @JvmField val error: TotalError? = null
) {
    // Secondary constructor to match Java's Total(Currency, TotalError) overload
    constructor(currency: Currency, error: TotalError?) : this(currency, false, error)

    companion object {
        @JvmField
        val ZERO = Total(Currency.EMPTY)

        @JvmStatic
        fun asIncomeExpense(currency: Currency, income: Long, expense: Long): Total {
            return Total(currency).apply {
                showIncomeExpense = true
                this.income = income
                this.expenses = expense
                this.balance = income + expense
            }
        }
    }

    @JvmField
    var amount: Long = 0
    
    @JvmField
    var balance: Long = 0

    @JvmField
    var showIncomeExpense: Boolean = false
    
    @JvmField
    var income: Long = 0
    
    @JvmField
    var expenses: Long = 0

    val isError: Boolean
        get() = error != null

    fun getError(context: Context): String {
        return error?.let {
            context.getString(
                R.string.rate_not_available_on_date_error,
                Utils.formatRateDate(context, it.datetime),
                it.currency,
                this.currency
            )
        } ?: context.getString(R.string.not_available)
    }
}
