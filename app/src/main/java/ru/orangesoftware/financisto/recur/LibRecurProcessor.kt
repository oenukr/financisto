package ru.orangesoftware.financisto.recur

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import java.util.TimeZone
import kotlin.time.Instant

class LibRecurProcessor(
    rruleString: String,
    startDate: Instant,
    private val timeZone: TimeZone
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

    override fun fastForward(until: Instant) {
        iterator.fastForward(DateTime(timeZone, until.toEpochMilliseconds()))
    }
}
