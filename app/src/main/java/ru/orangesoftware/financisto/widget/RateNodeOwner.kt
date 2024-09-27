package ru.orangesoftware.financisto.widget

import android.app.Activity

import ru.orangesoftware.financisto.model.Currency

interface RateNodeOwner {
    fun onBeforeRateDownload()
    fun onAfterRateDownload()
    fun onSuccessfulRateDownload()
    fun onRateChanged()

    fun getActivity(): Activity

    fun getCurrencyFrom(): Currency
    fun getCurrencyTo(): Currency
}
