package ru.orangesoftware.financisto.activity;

/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Abdsandryk Souza 
 *     Rodrigo Sousa
 *******************************************************************************/

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;

import java.util.Collection;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.utils.CurrencyCache;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.PinProtection;

public class ReportPreferencesActivity extends PreferenceActivity {

	/**
	 * The list of currencies.
	 */
	private String[] currencies;
	
	private String currency;
		
	/**
	 * The index of the selected currency
	 */
	private int selectedCurrenceIndex;

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MyPreferences.switchLocale(base));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);   
		addPreferencesFromResource(R.xml.report_preferences);	
		
		getCurrenciesList();		
		final EditTextPreference pReportReferenceCurrency = (EditTextPreference)getPreferenceScreen().findPreference("report_reference_currency");
		pReportReferenceCurrency.setOnPreferenceClickListener(
                arg0 -> showChoiceList(pReportReferenceCurrency)
        );
	}

	/**
	 * Get the list of currencies.
	 */
	private void getCurrenciesList() {
		String selectedCurrenceTitle = MyPreferences.getReferenceCurrencyTitle(this);
		Collection<Currency> currenciesList = CurrencyCache.getAllCurrencies();
		
		int count = currenciesList.size();

		selectedCurrenceIndex = -1;		
		currencies = new String[count];
        int i=0;
        for (Currency c : currenciesList) {
            if (c.title.equals(selectedCurrenceTitle)) {
                selectedCurrenceIndex = i;
            }
            currencies[i] = c.title;
            i++;
        }
	}


	/**
	 * Popup currencies options.
	 * @param pReportReferenceCurrency
	 * @return
	 */
	private boolean showChoiceList(final EditTextPreference pReportReferenceCurrency) {
        // get user preferred currency
        new AlertDialog.Builder(ReportPreferencesActivity.this)
		.setTitle(R.string.report_reference_currency)
		.setPositiveButton(R.string.ok, (dialog, which) -> pReportReferenceCurrency.setText(currency))
		.setSingleChoiceItems(currencies, selectedCurrenceIndex, (dialog, which) -> {
            selectedCurrenceIndex = which;
            currency = currencies[which];
        })
		.show();
		
		Dialog dialog = pReportReferenceCurrency.getDialog();
		if(dialog!=null)
			dialog.cancel();
		return true;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		PinProtection.lock(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		PinProtection.unlock(this);
	}
}
