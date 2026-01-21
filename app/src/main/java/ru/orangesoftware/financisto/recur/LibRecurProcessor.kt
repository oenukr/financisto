package ru.orangesoftware.financisto.recur

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import kotlin.time.Instant
import java.util.TimeZone

class LibRecurProcessor(
    rruleString: String,
    startDate: Instant,
    timeZone: TimeZone
) : RecurrenceProcessor {

    private val iterator: RecurrenceRuleIterator

    init {
        val rule = RecurrenceRule(rruleString)
        val startDateTime = DateTime(timeZone, startDate.toEpochMilliseconds())
        iterator = rule.iterator(startDateTime)
    }

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): Instant? {
        if (!hasNext()) return null
        return Instant.fromEpochMilliseconds(iterator.nextDateTime().timestamp)
    }
}
