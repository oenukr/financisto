package ru.orangesoftware.financisto.recur

import com.google.ical.iter.RecurrenceIterator
import com.google.ical.iter.RecurrenceIteratorFactory
import com.google.ical.values.RRule
import java.text.ParseException
import java.util.Calendar
import java.util.Date
import kotlin.time.Instant

open class DateRecurrenceIterator(private val processor: RecurrenceProcessor) {

    @JvmField
    internal var firstDate: Date? = null

    open fun hasNext(): Boolean {
        return firstDate != null || processor.hasNext()
    }

    open fun next(): Date? {
        if (firstDate != null) {
            val date: Date? = firstDate
            firstDate = null
            return date
        }
        return processor.next()?.let { Date(it.toEpochMilliseconds()) }
    }

    companion object {
        @JvmStatic
        @Throws(ParseException::class)
        fun create(rrule: RRule, nowDate: Date, startDate: Date): DateRecurrenceIterator {
            val rruleString = rrule.toIcal().replace("RRULE:", "")
            val timeZone = Calendar.getInstance().timeZone
            val startInstant = Instant.fromEpochMilliseconds(startDate.time)
            val nowInstant = Instant.fromEpochMilliseconds(nowDate.time)

            val processor: RecurrenceProcessor = LibRecurProcessor(rruleString, startInstant, timeZone)
            
            var date: Date? = null
            while (processor.hasNext()) {
                val next = processor.next()
                if (next != null) {
                    val d = Date(next.toEpochMilliseconds())
                    if (!d.before(nowDate)) {
                        date = d
                        break
                    }
                }
            }
            val iterator = DateRecurrenceIterator(processor)
            iterator.firstDate = date
            return iterator
        }

        @JvmStatic
        fun empty(): DateRecurrenceIterator {
            return DateRecurrenceIterator(EmptyRecurrenceProcessor)
        }
    }

    private object EmptyRecurrenceProcessor : RecurrenceProcessor {
        override fun hasNext(): Boolean = false
        override fun next(): Instant? = null
    }
}