package ru.orangesoftware.financisto.rates;

import org.json.JSONObject;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.http.HttpClientWrapper;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.utils.Logger;

/**
 * Created by vteremasov on 11/8/17.
 */

public class FreeCurrencyRateDownloader extends AbstractMultipleRatesDownloader {

    private final Logger logger = new DependenciesHolder().getLogger();

    private final HttpClientWrapper client;
    private final long dateTime;

    public FreeCurrencyRateDownloader(HttpClientWrapper client, long dateTime) {
        this.client = client;
        this.dateTime = dateTime;
    }

    @Override
    public ExchangeRate getRate(Currency fromCurrency, Currency toCurrency) {
        ExchangeRate rate = createRate(fromCurrency, toCurrency);
        try {
            String s = getResponse(fromCurrency, toCurrency);
            rate.rate = Double.parseDouble(s);
            return rate;
        } catch (Exception e) {
            rate.error = "Unable to get exchange rates: "+e.getMessage();
        }
        return rate;
    }

    @Override
    public ExchangeRate getRate(Currency fromCurrency, Currency toCurrency, long atTime) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private String getResponse(Currency fromCurrency, Currency toCurrency) throws Exception {
        String url = buildUrl(fromCurrency, toCurrency);
        logger.i(url);
        JSONObject jsonObject = client.getAsJson(url);
        logger.i(jsonObject.getString(toCurrency.name));
        return jsonObject.getString(toCurrency.name);
    }

    private ExchangeRate createRate(Currency fromCurrency, Currency toCurrency) {
        ExchangeRate rate = new ExchangeRate();
        rate.fromCurrencyId = fromCurrency.id;
        rate.toCurrencyId = toCurrency.id;
        rate.date = dateTime;
        return rate;
    }

    private String buildUrl (Currency fromCurrency, Currency toCurrency) {
        return "https://freecurrencyrates.com/api/action.php?s=fcr&iso="+toCurrency.name+"&f="+fromCurrency.name+"&v=1&do=cvals";
    }
}
