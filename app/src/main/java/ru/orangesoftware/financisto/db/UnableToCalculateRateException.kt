package ru.orangesoftware.financisto.db

import ru.orangesoftware.financisto.model.Currency

data class UnableToCalculateRateException(
    val fromCurrency: Currency,
    val toCurrency: Currency,
    val datetime: Long,
) : Exception()
