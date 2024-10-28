package ru.orangesoftware.financisto.rates

import android.content.SharedPreferences

import okhttp3.OkHttpClient
import ru.orangesoftware.financisto.http.HttpClientWrapper

enum class ExchangeRateProviderFactory {

    webservicex {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return WebserviceXConversionRateDownloader(createDefaultWrapper(), System.currentTimeMillis())
        }
    },
    openexchangerates {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            val appId: String? = sharedPreferences.getString("openexchangerates_app_id", "")
            return OpenExchangeRatesDownloader(createDefaultWrapper(), appId)
        }
    },
    freeCurrency {
        override fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider {
            return FreeCurrencyRateDownloader(createDefaultWrapper(), System.currentTimeMillis())
        }
    };

    abstract fun createProvider(sharedPreferences: SharedPreferences): ExchangeRateProvider

    companion object {
        private fun createDefaultWrapper(): HttpClientWrapper {
            return HttpClientWrapper(OkHttpClient())
        }
    }
}
