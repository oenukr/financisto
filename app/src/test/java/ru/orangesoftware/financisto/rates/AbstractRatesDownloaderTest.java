package ru.orangesoftware.financisto.rates;

import static org.junit.Assert.assertEquals;

import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import ru.orangesoftware.financisto.http.FakeHttpClientWrapper;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.http.TestKoinHelper;

import org.junit.After;
import org.junit.Before;

@RunWith(RobolectricTestRunner.class)
public abstract class AbstractRatesDownloaderTest {

    private final Map<String, Currency> nameToCurrency = new HashMap<>();
    private final AtomicLong counter = new AtomicLong(1);

    final FakeHttpClientWrapper client = new FakeHttpClientWrapper();

    protected AutoCloseable closeable;

    @Before
    public void setUp() {
        TestKoinHelper.INSTANCE.start(client);
        closeable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
        TestKoinHelper.INSTANCE.stop();
    }

    abstract ExchangeRateProvider service();

    ExchangeRate downloadRate(String from, String to) {
        return service().getRate(currency(from), currency(to));
    }

    Currency currency(String name) {
        Currency c = nameToCurrency.get(name);
        if (c == null) {
            c = new Currency();
            c.id = counter.getAndIncrement();
            c.name = name;
            nameToCurrency.put(name, c);
        }
        return c;
    }

    List<Currency> currencies(String... currencies) {
        List<Currency> list = new ArrayList<>();
        for (String name : currencies) {
            list.add(currency(name));
        }
        return list;
    }

    void givenResponseFromWebService(String url, String response) {
        client.givenResponse(url, response);
    }

    void givenExceptionWhileRequestingWebService() {
        client.error = new Exception("Timeout");
    }

    void assertRate(ExchangeRate exchangeRate, String fromCurrency, String toCurrency) {
        assertEquals("Expected " + fromCurrency, currency(fromCurrency).id, exchangeRate.fromCurrencyId);
        assertEquals("Expected " + toCurrency, currency(toCurrency).id, exchangeRate.toCurrencyId);
    }

    void assertRate(ExchangeRate exchangeRate, String fromCurrency, String toCurrency, double rate, long date) {
        assertRate(exchangeRate, fromCurrency, toCurrency);
        assertEquals(rate, exchangeRate.rate, 0.000001);
        assertEquals(date, exchangeRate.date);
    }

    static String anyUrl() {
        return "*";
    }

}
