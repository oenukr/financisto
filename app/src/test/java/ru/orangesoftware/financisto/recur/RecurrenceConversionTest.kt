package ru.orangesoftware.financisto.recur

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RecurrenceConversionTest {

    @Test
    fun `RecurrencePeriod should format state string correctly`() {
        val period = RecurrencePeriod(RecurrenceUntil.EXACTLY_TIMES, "count@5#")
        assertEquals("EXACTLY_TIMES:count@5#", period.stateToString())
    }

    @Test
    fun `RecurrencePeriod toRRuleString should format UNTIL in UTC`() {
        val startDate = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // stopsOnDate is "20260210T000000" (DateUtils.FORMAT_DATE_RFC_2445)
        val period = RecurrencePeriod(RecurrenceUntil.STOPS_ON_DATE, "date@20260210T000000#")
        val rrulePart = period.toRRuleString(startDate)
        assertEquals(";UNTIL=20260210T100000Z", rrulePart)
    }
}
