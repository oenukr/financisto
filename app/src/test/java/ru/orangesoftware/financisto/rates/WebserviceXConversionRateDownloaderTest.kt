package ru.orangesoftware.financisto.rates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import ru.orangesoftware.financisto.utils.Logger
import kotlin.time.Clock
import kotlin.time.Instant

class WebserviceXConversionRateDownloaderTest : AbstractRatesDownloaderTest() {

    @Mock
    lateinit var logger: Logger
    @Mock
    lateinit var clock: Clock
    
    private lateinit var webserviceX: WebserviceXConversionRateDownloader

    @Before
    override fun setUp() {
        super.setUp()
        MockitoAnnotations.openMocks(this)
        `when`(clock.now()).thenReturn(Instant.fromEpochMilliseconds(2000000L))
        webserviceX = WebserviceXConversionRateDownloader(client, logger, clock)
    }

    override fun service(): ExchangeRateProvider = webserviceX

    @Test
    fun should_download_single_rate_cur_to_cur() {
        // given
        givenResponseFromWebService("USD", "SGD", 1.2387)
        // when
        val exchangeRate = downloadRate("USD", "SGD")
        // then
        assertEquals(1.2387, exchangeRate.rate, 0.0001)
        assertEquals(2000000L, exchangeRate.date)
    }

    @Test
    fun should_handle_error_from_webservice_properly() {
        // given
        givenResponseFromWebService(anyUrl(),
                "System.IO.IOException: There is not enough space on the disk.\r\nStacktrace...")
        // when
        val downloadedRate = downloadRate("USD", "SGD")
        // then
        assertFalse(downloadedRate.isOk)
        assertEquals(
                "Something wrong with the exchange rates provider. Response from the service - System.IO.IOException: There is not enough space on the disk.",
                downloadedRate.errorMessage)
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

    private fun givenResponseFromWebService(c1: String, c2: String, r: Double) {
        givenResponseFromWebService(
                "https://www.webservicex.net/CurrencyConvertor.asmx/ConversionRate?FromCurrency=$c1&ToCurrency=$c2",
                "<double xmlns=\"https://www.webserviceX.NET/\">$r</double>")
    }
}
