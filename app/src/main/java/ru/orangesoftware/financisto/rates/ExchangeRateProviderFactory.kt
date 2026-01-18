package ru.orangesoftware.financisto.rates

import android.content.SharedPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

import org.koin.core.parameter.parametersOf

enum class ExchangeRateProviderFactory : KoinComponent {

    webservicex {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return get<WebserviceXConversionRateDownloader>()
        }
    },
    openexchangerates {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            val appId: String? = sharedPreferences.getString("openexchangerates_app_id", "")
            return get<OpenExchangeRatesDownloader> { parametersOf(appId) }
        }
    },
    freeCurrency {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return get<FreeCurrencyRateDownloader>()
        }
    };

    abstract fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider
}
