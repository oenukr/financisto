package ru.orangesoftware.financisto.rates

import ru.orangesoftware.financisto.model.Currency

interface ExchangeRateProvider {
    fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate
    fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate
    fun getRates(currencies: List<Currency>): List<ExchangeRate>
}
