package ru.orangesoftware.financisto.graph

import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.Utils
import kotlin.math.abs

class Amount(
	val currencyCache: CurrencyCache,
	val currency: Currency,
	val amount: Long,
) : Comparable<Amount> {
	
	var amountTextWidth: Int = 0
	var amountTextHeight: Int = 0

	fun getAmountText(): String =
		Utils.amountToString(currencyCache, currency, amount, true)

    override fun compareTo(other: Amount): Int {
        val thisAmount = abs(this.amount)
        val otherAmount = abs(other.amount)
        return otherAmount.compareTo(thisAmount)
    }

}
