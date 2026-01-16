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
) : AbstractMultipleRatesDownloader() {

    private val pattern = Pattern.compile("<double.*?>(.+?)</double>")

    override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        return createRate(fromCurrency, toCurrency).apply {
            try {
                val s = getResponse(fromCurrency, toCurrency)
                val m = pattern.matcher(s)
                if (m.find()) {
                    val rateValue = m.group(1)?.toDoubleOrNull()
                    if (rateValue != null) {
                        rate = rateValue
                    } else {
                        error = "Invalid rate format: ${m.group(1)}"
                    }
                } else {
                    error = parseError(s)
                }
            } catch (e: Exception) {
                error = "Unable to get exchange rates: ${e.message}"
            }
        }
    }

    private fun createRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        return ExchangeRate().apply {
            fromCurrencyId = fromCurrency.id
            toCurrencyId = toCurrency.id
            date = clock.now().toEpochMilliseconds()
        }
    }

    private fun getResponse(fromCurrency: Currency, toCurrency: Currency): String {
        val url = buildUrl(fromCurrency, toCurrency)
        logger.i(url)
        val s = httpClientWrapper.getAsString(url).orEmpty()
        logger.i(s)
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
        throw UnsupportedOperationException("Not supported by WebserviceX.NET")
    }
}
