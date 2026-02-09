package ru.orangesoftware.financisto.recur

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

class RecurrenceConversionTest {

    @Before
    fun setUp() {
        RecurrenceTestHelper.start()
    }

    @After
    fun tearDown() {
        RecurrenceTestHelper.stop()
    }

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

    @Test(expected = IllegalArgumentException::class)
    fun `RecurrencePeriod toRRuleString should throw exception on malformed date`() {
        val startDate = Calendar.getInstance()
        val period = RecurrencePeriod(RecurrenceUntil.STOPS_ON_DATE, "date@INVALID_DATE#")
        period.toRRuleString(startDate)
    }

    @Test
    fun `Recurrence createIterator should return empty iterator on malformed rule`() {
        val recurrence = Recurrence().apply {
            updateStartDate(2026, 0, 1)
            pattern = RecurrencePattern(RecurrenceFrequency.DAILY, "interval@1#")
            period = RecurrencePeriod(RecurrenceUntil.STOPS_ON_DATE, "date@INVALID_DATE#")
        }
        val iterator = recurrence.createIterator(Date())
        assertFalse("Iterator should be empty for malformed rule", iterator.hasNext())
    }
}
