/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;

import gnu.trove.map.hash.TLongObjectHashMap;
import ru.orangesoftware.financisto.db.CurrencyDao;
import ru.orangesoftware.financisto.model.Currency;

public class CurrencyCache {

    private final TLongObjectHashMap<Currency> currencies = new TLongObjectHashMap<>();
    private final CurrencyDao currencyDao;

    public CurrencyCache(CurrencyDao currencyDao) {
        this.currencyDao = currencyDao;
        initialize();
    }

    public synchronized Currency getCurrency(long currencyId) {
        Currency cachedCurrency = currencies.get(currencyId);
        if (cachedCurrency == null) {
            cachedCurrency = currencyDao.get(currencyId);
            if (cachedCurrency == null) {
                cachedCurrency = Currency.Companion.getEMPTY();
            }
            currencies.put(currencyId, cachedCurrency);
        }
        return cachedCurrency;
    }

	public synchronized Currency getCurrencyOrEmpty(long currencyId) {
		Currency c = currencies.get(currencyId);
		return c != null ? c : Currency.Companion.getEMPTY();
	}

	public synchronized void initialize() {
		TLongObjectHashMap<Currency> newCurrencies = new TLongObjectHashMap<>();
		List<Currency> currencyList = currencyDao.getAll();
		for (Currency currency : currencyList) {
		    newCurrencies.put(currency.getId(), currency);
        }
		this.currencies.putAll(newCurrencies);
	}

	public DecimalFormat createCurrencyFormat(Currency c) {
		DecimalFormatSymbols dfs = new DecimalFormatSymbols();
		dfs.setDecimalSeparator(charOrEmpty(c.getDecimalSeparator(), dfs.getDecimalSeparator()));
		dfs.setGroupingSeparator(charOrEmpty(c.getGroupSeparator(), dfs.getGroupingSeparator()));
		dfs.setMonetaryDecimalSeparator(dfs.getDecimalSeparator());
		dfs.setCurrencySymbol(c.getSymbol());

		DecimalFormat df = new DecimalFormat("#,##0.00", dfs);
		df.setGroupingUsed(dfs.getGroupingSeparator() > 0);
		df.setMinimumFractionDigits(c.getDecimals());
		df.setMaximumFractionDigits(c.getDecimals());
		df.setDecimalSeparatorAlwaysShown(false);
		return df;
	}

	private static char charOrEmpty(String s, char c) {
		return s != null ? (s.length() > 2 ? s.charAt(1) : 0): c;
	}

	public synchronized Collection<Currency> getAllCurrencies() {
		return currencies.valueCollection();
	}


}
