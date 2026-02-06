package ru.orangesoftware.financisto.recur

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone
import kotlin.time.Instant

class LibRecurProcessorTest {

    @Test
    fun `LibRecurProcessor should generate next daily occurrences`() {
        val start = Instant.parse("2024-01-01T10:00:00Z")
        val rrule = "FREQ=DAILY;INTERVAL=1"
        val processor = LibRecurProcessor(rrule, start, TimeZone.getTimeZone("UTC"))
        
        assertEquals(true, processor.hasNext())
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), processor.next())
        assertEquals(Instant.parse("2024-01-02T10:00:00Z"), processor.next())
    }
}
