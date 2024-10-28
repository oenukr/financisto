package ru.orangesoftware.financisto.export.csv

import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.MyEntity
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.Transaction
import java.util.Calendar
import java.util.Date

class CsvTransaction {

    var date: Date? = null
    var time: Date? = null
    var fromAccountId: Long = 0
    var fromAmount: Long = 0
    var originalAmount: Long = 0
    var originalCurrency: String? = null
    var payee: String? = null
    var category: String? = null
    var categoryParent: String? = null
    var note: String? = null
    var project: String? = null
    var currency: String? = null
    var delta: Long = 0

    fun createTransaction(
        currencies: Map<String, Currency>,
        categories: Map<String, Category>,
        projects: Map<String, Project>,
        payees: Map<String, Payee>,
    ): Transaction {
        val t: Transaction = Transaction()
        t.dateTime = combineToMillis(date, time, delta)
        t.fromAccountId = fromAccountId
        t.fromAmount = fromAmount
        t.categoryId = getEntityIdOrZero(categories, category)
        t.payeeId = getEntityIdOrZero(payees, payee)
        t.projectId = getEntityIdOrZero(projects, project)
        if (originalAmount != 0L) {
            val currency: Currency? = currencies[originalCurrency]
            t.originalFromAmount = originalAmount
            t.originalCurrencyId = currency?.id ?: 0
        }
        t.note = note
        return t
    }

    private fun combineToMillis(date: Date?, time: Date?, delta: Long): Long {
        val dateC: Calendar = emptyCalendar(date)
        val dateT: Calendar = emptyCalendar(time)
        val c: Calendar = Calendar.getInstance()
        copy(Calendar.YEAR, dateC, c)
        copy(Calendar.MONTH, dateC, c)
        copy(Calendar.DAY_OF_MONTH, dateC, c)
        copy(Calendar.HOUR_OF_DAY, dateT, c)
        copy(Calendar.MINUTE, dateT, c)
        copy(Calendar.SECOND, dateT, c)
        c.set(Calendar.MILLISECOND, 0)
        return c.getTimeInMillis() + delta
    }

    private fun emptyCalendar(date: Date?): Calendar {
        val c: Calendar = Calendar.getInstance()
        c.clear()
        c.setTimeInMillis(date?.time ?: 0)
        return c
    }

    private fun copy(field: Int, fromC: Calendar, toC: Calendar) {
        toC.set(field, fromC.get(field))
    }

    companion object {
        private fun <T : MyEntity> getEntityIdOrZero(map: Map<String, T>, value: String?): Long {
            val e: T? = map[value]
            return e?.id ?: 0
        }
    }
}
