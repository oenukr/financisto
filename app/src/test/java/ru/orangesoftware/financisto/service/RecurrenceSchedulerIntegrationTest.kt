package ru.orangesoftware.financisto.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.recur.RecurrenceTestHelper
import java.util.Calendar
import java.util.TimeZone

class RecurrenceSchedulerIntegrationTest {

    @Before
    fun setUp() {
        RecurrenceTestHelper.start()
    }

    @After
    fun tearDown() {
        RecurrenceTestHelper.stop()
    }

    @Test
    fun `should restore missed daily schedules`() {
        val timeZone = Calendar.getInstance().timeZone
        val last = calendar(2026, 2, 5, 10, 0, 0, timeZone)
        val now = calendar(2026, 2, 9, 12, 0, 0, timeZone)
        
        // Use ISO 8601 without 'Z' to represent local time if needed, but start date in Recurrence.parse
        // is parsed using FORMAT_TIMESTAMP_ISO_8601 which expects 'Z' or offset?
        // Let's check DateUtils.FORMAT_TIMESTAMP_ISO_8601.
        
        val recurrenceStr = "2026-02-01T10:00:00Z~DAILY:interval@1#~INDEFINITELY:"
        
        val t = TransactionInfo().apply {
            id = 1
            recurrence = recurrenceStr
            lastRecurrence = last.timeInMillis
        }
        
        val db = RecurrenceSchedulerTest.FakeDatabaseAdapter(null, arrayListOf(t))
        val scheduler = RecurrenceScheduler(db)
        
        val missed = scheduler.getMissedSchedules(now.timeInMillis)
        
        assertEquals(4, missed.size)
        assertEquals(calendar(2026, 2, 6, 10, 0, 0, timeZone).time, missed[0].dateTime)
        assertEquals(calendar(2026, 2, 7, 10, 0, 0, timeZone).time, missed[1].dateTime)
        assertEquals(calendar(2026, 2, 8, 10, 0, 0, timeZone).time, missed[2].dateTime)
        assertEquals(calendar(2026, 2, 9, 10, 0, 0, timeZone).time, missed[3].dateTime)
    }

    @Test
    fun `should handle monthly recurrence on the last day using SPECIFIC_DAY LAST`() {
        val timeZone = Calendar.getInstance().timeZone
        val last = calendar(2026, 1, 31, 10, 0, 0, timeZone)
        val now = calendar(2026, 3, 1, 12, 0, 0, timeZone)
        
        val recurrenceStr = "2026-01-31T10:00:00Z~MONTHLY:interval@1#monthly_pattern_0@SPECIFIC_DAY#monthly_pattern_params_0@LAST-DAY#~INDEFINITELY:"
        
        val t = TransactionInfo().apply {
            id = 1
            recurrence = recurrenceStr
            lastRecurrence = last.timeInMillis
        }
        
        val db = RecurrenceSchedulerTest.FakeDatabaseAdapter(null, arrayListOf(t))
        val scheduler = RecurrenceScheduler(db)
        
        val missed = scheduler.getMissedSchedules(now.timeInMillis)
        
        assertEquals(1, missed.size)
        assertEquals(calendar(2026, 2, 28, 10, 0, 0, timeZone).time, missed[0].dateTime)
    }

    @Test
    fun `should handle monthly recurrence every 15th`() {
        val timeZone = Calendar.getInstance().timeZone
        val last = calendar(2026, 1, 15, 10, 0, 0, timeZone)
        val now = calendar(2026, 3, 16, 12, 0, 0, timeZone)
        
        val recurrenceStr = "2026-01-15T10:00:00Z~MONTHLY:interval@1#monthly_pattern_0@EVERY_NTH_DAY#monthly_pattern_params_0@15#~INDEFINITELY:"
        
        val t = TransactionInfo().apply {
            id = 1
            recurrence = recurrenceStr
            lastRecurrence = last.timeInMillis
        }
        
        val db = RecurrenceSchedulerTest.FakeDatabaseAdapter(null, arrayListOf(t))
        val scheduler = RecurrenceScheduler(db)
        
        val missed = scheduler.getMissedSchedules(now.timeInMillis)
        
        assertEquals(2, missed.size)
        assertEquals(calendar(2026, 2, 15, 10, 0, 0, timeZone).time, missed[0].dateTime)
        assertEquals(calendar(2026, 3, 15, 10, 0, 0, timeZone).time, missed[1].dateTime)
    }

    @Test
    fun `should respect UNTIL period`() {
        val timeZone = Calendar.getInstance().timeZone
        val last = calendar(2026, 2, 1, 10, 0, 0, timeZone)
        val now = calendar(2026, 2, 10, 12, 0, 0, timeZone)
        
        // UNTIL is in UTC in RRule string
        val recurrenceStr = "2026-02-01T10:00:00Z~DAILY:interval@1#~STOPS_ON_DATE:date@20260203T000000#"
        
        val t = TransactionInfo().apply {
            id = 1
            recurrence = recurrenceStr
            lastRecurrence = last.timeInMillis
        }
        
        val db = RecurrenceSchedulerTest.FakeDatabaseAdapter(null, arrayListOf(t))
        val scheduler = RecurrenceScheduler(db)
        
        val missed = scheduler.getMissedSchedules(now.timeInMillis)
        
        assertEquals(2, missed.size)
        assertEquals(calendar(2026, 2, 2, 10, 0, 0, timeZone).time, missed[0].dateTime)
        assertEquals(calendar(2026, 2, 3, 10, 0, 0, timeZone).time, missed[1].dateTime)
    }

    private fun calendar(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int, tz: TimeZone): Calendar {
        return Calendar.getInstance(tz).apply {
            set(y, m - 1, d, h, min, s)
            set(Calendar.MILLISECOND, 0)
        }
    }
}