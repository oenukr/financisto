package ru.orangesoftware.financisto.recur

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class RecurrenceProcessorTest {

    @Test
    fun `RecurrenceResult should hold next date`() {
        val fixedTime = Instant.parse("2024-01-01T00:00:00Z")
        // RecurrenceResult doesn't exist yet
        val result = RecurrenceResult(fixedTime) 
        assertEquals(fixedTime, result.date)
    }

    @Test
    fun `RecurrenceProcessor should define iterator methods`() {
        // RecurrenceProcessor doesn't exist yet
        val processor = object : RecurrenceProcessor {
             override fun hasNext(): Boolean {
                 return false
             }
             
             override fun next(): Instant? {
                 return null
             }

             override fun fastForward(until: Instant) {}
        }
        assertEquals(false, processor.hasNext())
    }
}
