package ru.orangesoftware.financisto.recur

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
            return create(rruleString, nowDate, startDate)
        }

        @JvmStatic
        fun create(rruleString: String, nowDate: Date, startDate: Date): DateRecurrenceIterator {
            val timeZone = Calendar.getInstance().timeZone
            val startInstant = Instant.fromEpochMilliseconds(startDate.time)
            val nowInstant = Instant.fromEpochMilliseconds(nowDate.time)

            val processor: RecurrenceProcessor = LibRecurProcessor(rruleString, startInstant, timeZone)
            processor.fastForward(nowInstant)

            val iterator = DateRecurrenceIterator(processor)
            iterator.firstDate = processor.next()?.let { Date(it.toEpochMilliseconds()) }
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
        override fun fastForward(until: Instant) {}
    }
}