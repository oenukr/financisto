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
}
