package ru.orangesoftware.financisto.recur

import com.google.ical.iter.RecurrenceIterator
import ru.orangesoftware.financisto.recur.RecurrencePeriod.dateValueToDate
import kotlin.time.Instant
import java.util.Date

class LegacyRecurrenceProcessor(private val ri: RecurrenceIterator?) : RecurrenceProcessor {
    override fun hasNext(): Boolean {
        return ri?.hasNext() ?: false
    }

    override fun next(): Instant? {
        val dateValue = ri?.next() ?: return null
        val date: Date = dateValueToDate(dateValue)
        return Instant.fromEpochMilliseconds(date.time)
    }
}
