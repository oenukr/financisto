package ru.orangesoftware.financisto.model

class TotalError(
    val currency: Currency,
    val datetime: Long,
) {
    companion object {
        @JvmStatic
        fun lastRateError(currency: Currency): TotalError =
            TotalError(currency, System.currentTimeMillis())

        @JvmStatic
        fun atDateRateError(currency: Currency, datetime: Long): TotalError =
            TotalError(currency, datetime)
    }
}
