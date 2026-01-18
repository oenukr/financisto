package ru.orangesoftware.financisto.rates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import ru.orangesoftware.financisto.utils.Logger
import kotlin.time.Clock
import kotlin.time.Instant

class FreeCurrencyRateDownloaderTest : AbstractRatesDownloaderTest() {

    @Mock
    lateinit var logger: Logger
    @Mock
    lateinit var clock: Clock
    
    private lateinit var downloader: FreeCurrencyRateDownloader

    @Before
    override fun setUp() {
        super.setUp() // This is a comment
        MockitoAnnotations.openMocks(this)
        `when`(clock.now()).thenReturn(Instant.fromEpochMilliseconds(1000000L))
        downloader = FreeCurrencyRateDownloader(client, logger, clock)
    }

    override fun service(): ExchangeRateProvider = downloader

    @Test
    fun should_download_single_rate() {
        // given
        givenResponseFromWebService(
            "https://freecurrencyrates.com/api/action.php?s=fcr&iso=SGD&f=USD&v=1&do=cvals",
            "{\"SGD\":\"1.25\"}"
        )
        // when
        val rate = downloadRate("USD", "SGD")
        // then
        assertTrue(rate.isOk)
        assertEquals(1.25, rate.rate, 0.00001)
        assertEquals(1000000L, rate.date)
    }

    @Test
    fun should_handle_error_properly() {
        // given
        givenExceptionWhileRequestingWebService()
        // when
        val rate = downloadRate("USD", "SGD")
        // then
        assertFalse(rate.isOk)
        assertEquals("Unable to get exchange rates: Timeout", rate.errorMessage)
    }
}
