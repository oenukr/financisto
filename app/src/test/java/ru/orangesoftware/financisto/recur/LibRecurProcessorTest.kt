package ru.orangesoftware.financisto.recur

import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class LibRecurProcessorTest {

    @Test
    fun `LibRecurProcessor should generate next daily occurrences`() {
        val start = Instant.parse("2024-01-01T10:00:00Z")
        val rrule = "FREQ=DAILY;INTERVAL=1"
        // LibRecurProcessor doesn't exist yet
        val processor = LibRecurProcessor(rrule, start, TimeZone.getTimeZone("UTC"))
        
        assertEquals(true, processor.hasNext())
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), processor.next())
        assertEquals(Instant.parse("2024-01-02T10:00:00Z"), processor.next())
    }
}
