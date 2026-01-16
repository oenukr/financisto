package ru.orangesoftware.financisto.rates

import ru.orangesoftware.financisto.http.HttpClientWrapper
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.Logger

class FreeCurrencyRateDownloader(
    private val httpClientWrapper: HttpClientWrapper,
    private val logger: Logger,
    private val dateTime: Long,
) : AbstractMultipleRatesDownloader() {

    override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        return createRate(fromCurrency, toCurrency).apply {
            try {
                val s = getResponse(fromCurrency, toCurrency)
                rate = s.toDouble()
            } catch (e: Exception) {
                error = "Unable to get exchange rates: ${e.message}"
            }
        }
    }

    override fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate {
        throw UnsupportedOperationException("Not yet implemented")
    }

    private fun getResponse(fromCurrency: Currency, toCurrency: Currency): String {
        val url = buildUrl(fromCurrency, toCurrency)
        logger.i(url)
        val jsonObject = httpClientWrapper.getAsJson(url)
        val result = jsonObject.getString(toCurrency.name)
        logger.i(result)
        return result
    }

    private fun createRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        return ExchangeRate().apply {
            fromCurrencyId = fromCurrency.id
            toCurrencyId = toCurrency.id
            date = dateTime
        }
    }

    private fun buildUrl(fromCurrency: Currency, toCurrency: Currency): String {
        return "https://freecurrencyrates.com/api/action.php?s=fcr&iso=${toCurrency.name}&f=${fromCurrency.name}&v=1&do=cvals"
    }
}
