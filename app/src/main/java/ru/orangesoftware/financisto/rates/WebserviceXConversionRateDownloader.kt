package ru.orangesoftware.financisto.rates

import kotlin.time.Clock
import ru.orangesoftware.financisto.http.HttpClientWrapper
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.Logger
import java.util.regex.Pattern

class WebserviceXConversionRateDownloader(
    private val httpClientWrapper: HttpClientWrapper,
    private val logger: Logger,
    private val clock: Clock
) : BaseExchangeRateDownloader(clock) {

    private val pattern = Pattern.compile("<double.*?>(.+?)</double>")

    override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        val exchangeRate = createRate(fromCurrency, toCurrency)
        exchangeRate.safeExecute {
            val s = getResponse(fromCurrency, toCurrency)
            val m = pattern.matcher(s)
            if (m.find()) {
                val rateValue = m.group(1)?.toDoubleOrNull()
                if (rateValue != null) {
                    exchangeRate.rate = rateValue
                } else {
                    exchangeRate.error = "Invalid rate format: ${m.group(1)}"
                }
            } else {
                exchangeRate.error = parseError(s)
            }
        }
        return exchangeRate
    }

    private fun getResponse(fromCurrency: Currency, toCurrency: Currency): String {
        val url = buildUrl(fromCurrency, toCurrency)
        logger.i("Downloading rates from WebserviceX: $url")
        val s = httpClientWrapper.getAsString(url).orEmpty()
        logger.i("Response: $s")
        return s
    }

    private fun parseError(s: String): String {
        val firstLine = s.lines().firstOrNull { it.isNotBlank() }
        return if (firstLine != null) {
            "Something wrong with the exchange rates provider. Response from the service - $firstLine"
        } else {
            "Service is not available, please try again later"
        }
    }

    private fun buildUrl(fromCurrency: Currency, toCurrency: Currency): String {
        return "https://www.webservicex.net/CurrencyConvertor.asmx/ConversionRate?FromCurrency=${fromCurrency.name}&ToCurrency=${toCurrency.name}"
    }

    override fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate {
        throw UnsupportedOperationException("Historical rates not supported yet by this downloader")
    }
}