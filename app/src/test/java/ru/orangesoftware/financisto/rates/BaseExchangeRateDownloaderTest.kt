package ru.orangesoftware.financisto.rates

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.orangesoftware.financisto.model.Currency
import kotlin.time.Clock
import kotlin.time.Instant

class BaseExchangeRateDownloaderTest {

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1000)
    }

    private val downloader = object : BaseExchangeRateDownloader(fixedClock) {
        override fun getRate(fromCurrency: Currency, toCurrency: Currency): ExchangeRate {
            return createRate(fromCurrency, toCurrency)
        }

        override fun getRate(fromCurrency: Currency, toCurrency: Currency, atTime: Long): ExchangeRate {
            throw UnsupportedOperationException()
        }
        
        fun testSafeExecute(shouldThrow: Boolean): ExchangeRate {
             val rate = ExchangeRate()
             rate.safeExecute {
                 if (shouldThrow) throw RuntimeException("Boom")
             }
             return rate
        }
    }

    @Test
    fun `createRate should set correct IDs and timestamp`() {
        val from = Currency().apply { id = 1 }
        val to = Currency().apply { id = 2 }
        
        val rate = downloader.getRate(from, to)
        
        assertEquals(1L, rate.fromCurrencyId)
        assertEquals(2L, rate.toCurrencyId)
        assertEquals(1000L, rate.date)
    }

    @Test
    fun `safeExecute should catch exception and set error message`() {
        val rate = downloader.testSafeExecute(true)
        assertEquals("Unable to get exchange rates: Boom", rate.error)
    }
    
    @Test
    fun `safeExecute should do nothing on success`() {
        val rate = downloader.testSafeExecute(false)
        assertEquals(null, rate.error)
    }
}
