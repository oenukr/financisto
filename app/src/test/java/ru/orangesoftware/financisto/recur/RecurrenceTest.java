package ru.orangesoftware.financisto.recur;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static ru.orangesoftware.financisto.test.DateTime.date;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import ru.orangesoftware.financisto.test.DateTime;
import timber.log.Timber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RecurrenceTest {

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Test
    public void test_should_schedule_correctly_monthly_last_working_day() throws ParseException {
        assertDates(
                "2010-06-30T15:20:00~MONTHLY:count@1#interval@1#monthly_pattern_params_0@LAST-WEEKDAY#monthly_pattern_0@SPECIFIC_DAY#~INDEFINITELY:null",
                date(2011, 2, 18).atMidnight(),
                "2011-02-28 15:20:00,2011-03-31 15:20:00,2011-04-29 15:20:00,...");
    }

    @Test
    public void test_should_schedule_correctly_on_the_same_day_if_the_schedule_time_is_after_the_current_time() throws Exception {
        assertDates(
                "2011-02-27T19:30:00~DAILY:interval@1#~INDEFINITELY:null",
                date(2011, 2, 27).at(12, 0, 0, 0),
                "2011-02-27 19:30:00,2011-02-28 19:30:00,...");
    }

    @Test
    public void test_should_schedule_correctly_on_the_next_day_if_the_scheduled_time_is_before_the_current_time() throws Exception {
        assertDates(
                "2011-02-27T19:30:00~DAILY:interval@1#~INDEFINITELY:null",
                date(2011, 2, 27).at(20, 0, 0, 0),
                "2011-02-28 19:30:00,2011-03-01 19:30:00,...");
    }

    @Test
    public void test_should_generate_scheduled_times_for_specific_period() throws Exception {
        assertDates(generateDates("2011-08-02T21:40:00~DAILY:interval@1#~INDEFINITELY:null", date(2011, 8, 1).atMidnight(), date(2011, 8, 5).atDayEnd()),
                "2011-08-02 21:40:00,2011-08-03 21:40:00,2011-08-04 21:40:00,2011-08-05 21:40:00");

        assertDates(generateDates("2011-08-02T21:40:00~DAILY:interval@2#~INDEFINITELY:null", date(2011, 8, 8).at(23, 20, 0, 0), date(2011, 8, 16).atDayEnd()),
                "2011-08-10 21:40:00,2011-08-12 21:40:00,2011-08-14 21:40:00,2011-08-16 21:40:00");

        assertDates(generateDates("2011-08-02T23:00:00~WEEKLY:days@TUE#interval@1#~INDEFINITELY:null", date(2011, 8, 8).at(23, 20, 0, 0), date(2011, 8, 16).atDayEnd()),
                "2011-08-09 23:00:00,2011-08-16 23:00:00");

        assertDates(generateDates("2011-08-02T21:20:00~WEEKLY:days@FRI#interval@1#~INDEFINITELY:null", date(2011, 8, 8).at(23, 20, 0, 0), date(2011, 8, 16).atDayEnd()),
                "2011-08-12 21:20:00");

        assertTrue(generateDates("2011-09-02T21:20:00~WEEKLY:days@FRI#interval@1#~INDEFINITELY:null", date(2011, 8, 8).at(23, 20, 0, 0), date(2011, 8, 16).atDayEnd()).isEmpty());
    }

    private List<Date> generateDates(String pattern, DateTime start, DateTime end) {
        Recurrence r = Recurrence.parse(pattern);
        return r.generateDates(start.asDate(), end.asDate());
    }

    private void assertDates(List<Date> dates, String datesAsString) throws ParseException {
        String[] expectedDates = datesAsString.split(",");
        logDates(expectedDates, dates);
        int count = expectedDates.length;
        if (count < dates.size()) {
            fail("Too many dates generated: Expected " + count + ", Got " + dates.size());
        }
        if (count > dates.size()) {
            fail("Too few dates generated: Expected " + count + ", Got " + dates.size());
        }
        for (int i = 0; i < count; i++) {
            String expectedDate = expectedDates[i];
            String actualDate = formatDateTime(dates.get(i));
            assertEquals(i + " -> Expected: " + expectedDate + " Got: " + actualDate, expectedDate, actualDate);
        }
    }

    private void logDates(String[] expectedDates, List<Date> dates) throws ParseException {
        Timber.d("===== Local timezone: %s", TimeZone.getDefault().toString());
        Timber.d("===== Current datetime: %s", Calendar.getInstance().toString());
        Timber.d("===== Expected dates: %d =====", expectedDates.length);
        for (String expectedDate : expectedDates) {
            Timber.d(expectedDate);
        }
        Timber.d("===== Actual dates: %d =====", dates.size());
        for (Date date : dates) {
            Timber.d(formatDateTime(date));
        }
        Timber.d("==========");
    }

    private void assertDates(String pattern, DateTime startDateTime, String datesAsString) throws ParseException {
        Recurrence r = Recurrence.parse(pattern);
        DateRecurrenceIterator ri = r.createIterator(startDateTime.asDate());
        assertTrue(ri.hasNext());
        String[] expectedDates = datesAsString.split(",");
        for (String expectedDate : expectedDates) {
            if ("...".equals(expectedDate)) {
                assertTrue(ri.hasNext());
                return;
            }
            assertEquals(expectedDate, formatDateTime(ri.next()));
        }
    }

    private String formatDateTime(Date d) throws ParseException {
        return sdf.format(d);
    }

}
