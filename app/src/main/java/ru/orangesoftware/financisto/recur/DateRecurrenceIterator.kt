package ru.orangesoftware.financisto.recur

import com.google.ical.iter.RecurrenceIterator
import com.google.ical.iter.RecurrenceIteratorFactory
import com.google.ical.values.RRule
import ru.orangesoftware.financisto.recur.RecurrencePeriod.dateToDateValue
import ru.orangesoftware.financisto.recur.RecurrencePeriod.dateValueToDate
import java.text.ParseException
import java.util.Calendar
import java.util.Date

open class DateRecurrenceIterator(private val ri: RecurrenceIterator?) {

    private var firstDate: Date? = null

	open fun hasNext(): Boolean {
		return firstDate != null || ri?.hasNext() ?: false
	}

    open fun next(): Date? {
        if (firstDate != null) {
            val date: Date? = firstDate
            firstDate = null
            return date
        }
        return dateValueToDate(ri?.next())
    }

    companion object {
        @JvmStatic
        @Throws(ParseException::class)
        fun create(rrule: RRule, nowDate: Date, startDate: Date): DateRecurrenceIterator {
            val ri: RecurrenceIterator = RecurrenceIteratorFactory.createRecurrenceIterator(
                rrule,
                dateToDateValue(startDate),
                Calendar.getInstance().getTimeZone(),
            )
            var date: Date? = null
            while (ri.hasNext()) {
                date = dateValueToDate(ri.next())
                if (!date.before(nowDate)) break
            }
            //ri.advanceTo(dateToDateValue(nowDate))
            val iterator: DateRecurrenceIterator = DateRecurrenceIterator(ri)
            iterator.firstDate = date
            return iterator
        }

        @JvmStatic
        fun empty(): DateRecurrenceIterator {
            return EmptyDateRecurrenceIterator
        }
    }

    private object EmptyDateRecurrenceIterator : DateRecurrenceIterator(null) {
        override fun hasNext(): Boolean = false
        override fun next(): Date? = null
    }
}
