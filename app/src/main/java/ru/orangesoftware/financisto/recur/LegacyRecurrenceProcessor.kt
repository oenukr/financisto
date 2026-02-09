package ru.orangesoftware.financisto.recur

import com.google.ical.iter.RecurrenceIterator
import java.util.Date
import kotlin.time.Instant

class LegacyRecurrenceProcessor(private val ri: RecurrenceIterator?) : RecurrenceProcessor {
    private var peeked: Instant? = null

    override fun hasNext(): Boolean {
        return peeked != null || (ri?.hasNext() ?: false)
    }

    override fun next(): Instant? {
        peeked?.let {
            val p = it
            peeked = null
            return p
        }
        val dateValue = ri?.next() ?: return null
        val date: Date = RecurrencePeriod.dateValueToDate(dateValue)
        return Instant.fromEpochMilliseconds(date.time)
    }

    override fun fastForward(until: Instant) {
        while (hasNext()) {
            val n = next()
            if (n != null && n.toEpochMilliseconds() >= until.toEpochMilliseconds()) {
                peeked = n
                break
            }
        }
    }
}
