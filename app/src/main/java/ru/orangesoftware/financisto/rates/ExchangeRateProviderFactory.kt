package ru.orangesoftware.financisto.rates

import android.content.SharedPreferences


import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.orangesoftware.financisto.http.HttpClientWrapper

enum class ExchangeRateProviderFactory : KoinComponent {

    webservicex {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return WebserviceXConversionRateDownloader(httpClientWrapper, System.currentTimeMillis())
        }
    },
    openexchangerates {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            val appId: String? = sharedPreferences.getString("openexchangerates_app_id", "")
            return OpenExchangeRatesDownloader(httpClientWrapper, appId)
        }
    },
    freeCurrency {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return FreeCurrencyRateDownloader(httpClientWrapper, System.currentTimeMillis())
        }
    };

    protected val httpClientWrapper: HttpClientWrapper by inject()

    abstract fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider
}
