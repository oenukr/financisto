package ru.orangesoftware.financisto.rates

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import ru.orangesoftware.financisto.http.FakeHttpClientWrapper
import ru.orangesoftware.financisto.utils.FileUtils
import ru.orangesoftware.financisto.utils.Logger
import kotlin.time.Clock
import kotlin.time.Instant

class OpenExchangeRatesDownloaderTest : AbstractRatesDownloaderTest() {

    @Mock
    lateinit var logger: Logger
    @Mock
    lateinit var clock: Clock
    @Mock
    lateinit var sharedPreferences: SharedPreferences
    
    private lateinit var openRates: OpenExchangeRatesDownloader

    @Before
    override fun setUp() {
        super.setUp()
        MockitoAnnotations.openMocks(this)
        `when`(clock.now()).thenReturn(Instant.fromEpochMilliseconds(1361034009000L))
        `when`(sharedPreferences.getString("openexchangerates_app_id", "")).thenReturn("MY_APP_ID")
        // Use the 'client' from AbstractRatesDownloaderTest
        openRates = OpenExchangeRatesDownloader(client, logger, sharedPreferences, clock)
    }

    override fun service(): ExchangeRateProvider = openRates

    @Test
    fun should_download_single_rate_usd_to_cur() {
        // given
        givenResponseFromWebService("https://openexchangerates.org/api/latest.json?app_id=MY_APP_ID",
                "open_exchange_normal_response.json")
        // when
        val downloadedExchangeRate = downloadRate("USD", "SGD")
        // then
        assertTrue(downloadedExchangeRate.isOk)
        assertEquals(1.236699, downloadedExchangeRate.rate, 0.00001)
        assertEquals(1361034009000L, downloadedExchangeRate.date)
    }

    @Test
    fun should_download_single_rate_cur_to_cur() {
        // given
        givenResponseFromWebService(anyUrl(), "open_exchange_normal_response.json")
        // then
        assertEquals(1.0 / 1.236699, downloadRate("SGD", "USD").rate, 0.00001)
        assertEquals(0.00010655, downloadRate("BYR", "CHF").rate, 0.00001)
    }

    @Test
    fun should_download_multiple_rates() {
        // given
        givenResponseFromWebService(anyUrl(), "open_exchange_normal_response.json")
        // when
        val rates = openRates.getRates(currencies("USD", "SGD", "RUB"))
        // then
        assertEquals(3, rates.size)
        assertRate(rates[0], "USD", "SGD", 1.236699, 1361034009000L)
        assertRate(rates[1], "USD", "RUB", 30.117065, 1361034009000L)
        assertRate(rates[2], "SGD", "RUB", 24.352785, 1361034009000L)
    }

    @Test
    fun should_skip_unknown_currency() {
        // given
        givenResponseFromWebService(anyUrl(), "open_exchange_normal_response.json")
        // when
        val rate = downloadRate("USD", "AAA")
        // then
        assertFalse(rate.isOk)
        assertRate(rate, "USD", "AAA")
    }

    @Test
    fun should_handle_error_from_webservice_properly() {
        // given
        givenResponseFromWebService(anyUrl(), "open_exchange_error_response.json")
        // when
        val downloadedRate = downloadRate("USD", "SGD")
        // then
        assertFalse(downloadedRate.isOk)
        assertRate(downloadedRate, "USD", "SGD")
        assertEquals("400 (invalid_app_id): Invalid App ID", downloadedRate.errorMessage)
    }

    @Test
    fun should_handle_runtime_error_properly() {
        // given
        givenExceptionWhileRequestingWebService()
        // when
        val downloadedRate = downloadRate("USD", "SGD")
        // then
        assertFalse(downloadedRate.isOk)
        assertEquals("Unable to get exchange rates: Timeout", downloadedRate.errorMessage)
    }

    @Test
    fun should_fetch_from_network_only_once_for_getRates() {
        // given
        var requestCount = 0
        val countingClient = object : FakeHttpClientWrapper() {
            override fun getAsString(url: String): String? {
                requestCount++
                return super.getAsString(url)
            }
        }
        countingClient.givenResponse(anyUrl(), FileUtils.testFileAsString("open_exchange_normal_response.json"))
        
        val countingOpenRates = OpenExchangeRatesDownloader(countingClient, logger, sharedPreferences, clock)
        
        // when
        val rates = countingOpenRates.getRates(currencies("USD", "SGD", "RUB"))
        
        // then
        assertEquals(3, rates.size)
        assertEquals(1, requestCount)
    }

    @Test
    fun should_fetch_from_network_every_time_for_getRate_when_stateless() {
        // given
        var requestCount = 0
        val countingClient = object : FakeHttpClientWrapper() {
            override fun getAsString(url: String): String? {
                requestCount++
                return super.getAsString(url)
            }
        }
        countingClient.givenResponse(anyUrl(), FileUtils.testFileAsString("open_exchange_normal_response.json"))
        
        val countingOpenRates = OpenExchangeRatesDownloader(countingClient, logger, sharedPreferences, clock)
        
        // when
        countingOpenRates.getRate(currency("USD"), currency("SGD"))
        countingOpenRates.getRate(currency("USD"), currency("RUB"))
        
        // then
        assertEquals(2, requestCount)
    }

    override fun givenResponseFromWebService(url: String, fileName: String) {
        super.givenResponseFromWebService(url, FileUtils.testFileAsString(fileName))
    }
}