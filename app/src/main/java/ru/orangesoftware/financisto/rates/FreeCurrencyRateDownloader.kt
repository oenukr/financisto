package ru.orangesoftware.financisto.rates

import kotlin.time.Clock
import ru.orangesoftware.financisto.http.HttpClientWrapper
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.Logger

class FreeCurrencyRateDownloader(
    private val httpClientWrapper: HttpClientWrapper,
    private val logger: Logger,
    private val clock: Clock,
) : BaseExchangeRateDownloader(clock) {

    override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        val exchangeRate = createRate(fromCurrency, toCurrency)
        exchangeRate.safeExecute {
            val s = getResponse(fromCurrency, toCurrency)
            exchangeRate.rate = s.toDouble()
        }
        return exchangeRate
    }

    override fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate {
        throw UnsupportedOperationException("Historical rates not supported yet by this downloader")
    }

    private fun getResponse(fromCurrency: Currency, toCurrency: Currency): String {
        val url = buildUrl(fromCurrency, toCurrency)
        logger.i("Downloading rates from FreeCurrencyRates: $url")
        val jsonObject = httpClientWrapper.getAsJson(url)
        val result = jsonObject.getString(toCurrency.name)
        logger.i("Response: $result")
        return result
    }

    private fun buildUrl(fromCurrency: Currency, toCurrency: Currency): String {
        return "https://freecurrencyrates.com/api/action.php?s=fcr&iso=${toCurrency.name}&f=${fromCurrency.name}&v=1&do=cvals"
    }
}