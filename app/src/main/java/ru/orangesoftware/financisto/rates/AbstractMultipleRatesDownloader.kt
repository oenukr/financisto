package ru.orangesoftware.financisto.rates

import ru.orangesoftware.financisto.model.Currency

abstract class AbstractMultipleRatesDownloader : ExchangeRateProvider {

    override fun getRates(currencies: List<Currency>): List<ExchangeRate> {
        val rates = mutableListOf<ExchangeRate>()
        val count = currencies.size
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val fromCurrency = currencies[i]
                val toCurrency = currencies[j]
                rates.add(getRate(fromCurrency, toCurrency))
            }
        }
        return rates
    }
}
