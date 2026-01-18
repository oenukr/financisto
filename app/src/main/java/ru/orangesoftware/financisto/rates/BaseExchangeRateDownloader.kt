package ru.orangesoftware.financisto.rates

import ru.orangesoftware.financisto.model.Currency
import kotlin.time.Clock

abstract class BaseExchangeRateDownloader(
    private val clock: Clock
) : AbstractMultipleRatesDownloader() {

    protected fun createRate(from: Currency, to: Currency): ExchangeRate {
        return ExchangeRate().apply {
            fromCurrencyId = from.id
            toCurrencyId = to.id
            date = clock.now().toEpochMilliseconds()
        }
    }

    protected fun ExchangeRate.safeExecute(block: () -> Unit) {
        runCatching {
            block()
        }.onFailure { e ->
            error = "Unable to get exchange rates: ${e.message}"
        }
    }
}
