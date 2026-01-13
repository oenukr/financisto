package ru.orangesoftware.financisto.rates

import android.content.SharedPreferences

enum class ExchangeRateProviderFactory {

    webservicex {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return WebserviceXConversionRateDownloader(System.currentTimeMillis())
        }
    },
    openexchangerates {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            val appId: String? = sharedPreferences.getString("openexchangerates_app_id", "")
            return OpenExchangeRatesDownloader(appId)
        }
    },
    freeCurrency {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return FreeCurrencyRateDownloader(System.currentTimeMillis())
        }
    };

    abstract fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider
}
