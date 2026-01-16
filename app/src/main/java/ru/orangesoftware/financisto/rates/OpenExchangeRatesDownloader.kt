package ru.orangesoftware.financisto.rates

import org.json.JSONException
import org.json.JSONObject
import ru.orangesoftware.financisto.http.HttpClientWrapper
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.utils.Logger

//@NotThreadSafe
class OpenExchangeRatesDownloader(
    private val httpClientWrapper: HttpClientWrapper,
    private val logger: Logger,
    private val appId: String?
) : AbstractMultipleRatesDownloader() {

    private var json: JSONObject? = null

    override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
        return createRate(fromCurrency, toCurrency).apply {
            try {
                downloadLatestRates()
                val currentJson = json
                if (currentJson != null) {
                    if (hasError(currentJson)) {
                        error = error(currentJson)
                    } else {
                        updateRate(currentJson, this, fromCurrency, toCurrency)
                    }
                }
            } catch (e: Exception) {
                error = error(e)
            }
        }
    }

    private fun createRate(fromCurrency: Currency, toCurrency: Currency) = ExchangeRate().apply {
        fromCurrencyId = fromCurrency.id
        toCurrencyId = toCurrency.id
    }

    private fun downloadLatestRates() {
        if (json == null) {
            if (appId.isNullOrEmpty()) {
                throw RuntimeException("App ID is not set")
            }
            logger.i("Downloading latest rates...")
            val response = httpClientWrapper.getAsJson(getLatestUrl())
            json = response
            logger.i(response.toString())
        }
    }

    private fun getLatestUrl() = "${GET_LATEST}$appId"

    private fun hasError(json: JSONObject) = json.optBoolean("error", false)

    private fun error(json: JSONObject): String {
        val status = json.optString("status")
        val message = json.optString("message")
        val description = json.optString("description")
        return "$status ($message): $description"
    }

    private fun error(e: Exception) = "Unable to get exchange rates: ${e.message}"

    @Throws(JSONException::class)
    private fun updateRate(json: JSONObject, exchangeRate: ExchangeRate, fromCurrency: Currency, toCurrency: Currency) {
        val rates = json.getJSONObject("rates")
        val usdFrom = rates.getDouble(fromCurrency.name)
        val usdTo = rates.getDouble(toCurrency.name)
        exchangeRate.rate = if (usdFrom != 0.0) usdTo / usdFrom else 0.0
        val timestamp = json.optLong("timestamp", -1L)
        exchangeRate.date = if (timestamp != -1L) timestamp * 1000 else System.currentTimeMillis()
    }

    override fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate {
        throw UnsupportedOperationException("Not yet implemented")
    }

    companion object {
        private const val GET_LATEST = "https://openexchangerates.org/api/latest.json?app_id="
    }
}
