package ru.orangesoftware.financisto.rates

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

enum class ExchangeRateProviderFactory : KoinComponent {

    webservicex {
        override fun createProvider(): ExchangeRateProvider {
            return get<WebserviceXConversionRateDownloader>()
        }
    },
    openexchangerates {
        override fun createProvider(): ExchangeRateProvider {
            return get<OpenExchangeRatesDownloader>()
        }
    },
    freeCurrency {
        override fun createProvider(): ExchangeRateProvider {
            return get<FreeCurrencyRateDownloader>()
        }
    };

    abstract fun createProvider(): ExchangeRateProvider
}
