package ru.orangesoftware.financisto.db

import android.util.Log
import org.junit.Test
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.RateBuilder
import ru.orangesoftware.financisto.test.TransactionBuilder
import java.util.Calendar
import kotlin.time.measureTime

private const val TAG = "TransactionsTotalCalculatorBenchmark"

class TransactionsTotalCalculatorBenchmark : AbstractDbTest() {
    private lateinit var currency1: Currency
    private lateinit var currency2: Currency

    private lateinit var account1: Account

    private lateinit var totalCalculator: TransactionsTotalCalculator

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        currency1 = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").create()
        currency2 = CurrencyBuilder.withDb(db).name("EUR").title("Euro").symbol("€").create()

        totalCalculator = TransactionsTotalCalculator(db, WhereFilter.empty())

        account1 = AccountBuilder.withDb(db).title("Cash").currency(currency1).create()
    }

    @Test
    fun should_benchmark_blotter_total_in_home_currency() {
        val yearAmountOfDataDuration = measureTime {
            val count: Int = 366
            val calendar: Calendar = Calendar.getInstance()
            for (i in count downTo  1) {
                val date: DateTime = DateTime.fromTimestamp(calendar.getTimeInMillis())
                createRates(date, i)
                createTransaction(date.atMidnight(), 1000)
                createTransaction(date.atNoon(), 2000)
                createTransaction(date.atDayEnd(), 3000)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        Log.d(TAG, "Time to create a year amount of data: ${yearAmountOfDataDuration.inWholeMilliseconds}ms")

        val totalCalculationDuration = measureTime {
            totalCalculator.getAccountBalance(currency2, account1.id)
        }
        Log.d(TAG, "Time to get account total: ${totalCalculationDuration.inWholeMilliseconds}ms")
    }

    private fun createTransaction(date: DateTime, amount: Long) {
        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(date)
            .amount(amount)
            .create()
    }

    private fun createRates(date: DateTime, count: Int) {
        RateBuilder.withDb(db)
            .from(currency1)
            .to(currency2)
            .at(date)
            .rate(1f / count)
            .create()
    }

}
