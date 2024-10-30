package ru.orangesoftware.financisto.recur

import android.util.Log
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.DateTime.date
import java.util.Date
import kotlin.time.measureTimedValue

class RecurrencePerformanceTest {

    @Throws(Exception::class)
    fun test_should_generate_scheduled_times_for_specific_period() {
        val dailyPattern = "2011-08-02T21:40:00~DAILY:interval@1#~INDEFINITELY:null"
        generateDates(dailyPattern, date(2011, 8, 1))
        generateDates(dailyPattern, date(2011, 9, 2))
        generateDates(dailyPattern, date(2011, 12, 2))
        generateDates(dailyPattern, date(2012, 9, 2))
        generateDates(dailyPattern, date(2014, 9, 2))
        generateDates(dailyPattern, date(2016, 9, 2))
    }

    private fun generateDates(pattern: String, date: DateTime): List<Date> {
        val start = date.atMidnight().asLong()
        val end = date.atDayEnd().asLong()
        val (dates, duration) = measureTimedValue {
            try {
                val r: Recurrence = Recurrence.parse(pattern)
                r.generateDates(Date(start), Date(end))
            } finally { }
        }
        Log.i("RecurrencePerformanceTest", "Generated $start-$end: ${duration.inWholeMilliseconds}ms")
        return dates
    }

}
