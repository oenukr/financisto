package ru.orangesoftware.financisto.rates

import kotlin.time.Clock
import org.json.JSONException
import org.json.JSONObject
import ru.orangesoftware.financisto.http.HttpClientWrapper
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.Logger

class OpenExchangeRatesDownloader(
    private val httpClientWrapper: HttpClientWrapper,
    private val logger: Logger,
    private val appId: String?,
    private val clock: Clock
) : BaseExchangeRateDownloader(clock) {

    private var cachedJson: JSONObject? = null

    override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        val exchangeRate = createRate(fromCurrency, toCurrency)
        exchangeRate.safeExecute {
            val json = getLatestRates()
            if (hasError(json)) {
                exchangeRate.error = parseErrorMessage(json)
            } else {
                updateRate(json, exchangeRate, fromCurrency, toCurrency)
            }
        }
        return exchangeRate
    }

    private fun getLatestRates(): JSONObject {
        cachedJson?.let { return it }
        
        if (appId.isNullOrEmpty()) {
            throw RuntimeException("App ID is not set")
        }
        
        logger.i("Downloading latest rates from OpenExchangeRates...")
        val response = httpClientWrapper.getAsJson(getLatestUrl())
        cachedJson = response
        logger.i("Response: $response")
        return response
    }

    private fun getLatestUrl() = "$BASE_URL$appId"

    private fun hasError(json: JSONObject) = json.optBoolean("error", false)

    private fun parseErrorMessage(json: JSONObject): String {
        val status = json.optString("status")
        val message = json.optString("message")
        val description = json.optString("description")
        return "$status ($message): $description".trim()
    }

    @Throws(JSONException::class)
    private fun updateRate(json: JSONObject, exchangeRate: ExchangeRate, fromCurrency: Currency, toCurrency: Currency) {
        val rates = json.getJSONObject("rates")
        
        if (!rates.has(fromCurrency.name)) {
            exchangeRate.error = "Currency not found: ${fromCurrency.name}"
            return
        }
        if (!rates.has(toCurrency.name)) {
            exchangeRate.error = "Currency not found: ${toCurrency.name}"
            return
        }

        val usdFrom = rates.getDouble(fromCurrency.name)
        val usdTo = rates.getDouble(toCurrency.name)
        
        exchangeRate.rate = if (usdFrom != 0.0) usdTo / usdFrom else 0.0
        
        val timestamp = json.optLong("timestamp", -1L)
        if (timestamp != -1L) {
            exchangeRate.date = timestamp * 1000
        } else {
            // exchangeRate.date is already initialized to clock.now() in createRate, 
            // but let's be explicit if we want to ensure it's set here if missing.
            // Actually BaseExchangeRateDownloader.createRate sets it.
        }
    }

    override fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate {
        throw UnsupportedOperationException("Historical rates not supported yet by this downloader")
    }

    companion object {
        private const val BASE_URL = "https://openexchangerates.org/api/latest.json?app_id="
    }
}