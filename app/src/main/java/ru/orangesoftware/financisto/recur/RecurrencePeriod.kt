package ru.orangesoftware.financisto.recur

import com.google.ical.util.TimeUtils
import com.google.ical.values.DateTimeValueImpl
import com.google.ical.values.DateValue
import com.google.ical.values.DateValueImpl
import com.google.ical.values.RRule
import com.google.ical.values.TimeValue
import ru.orangesoftware.financisto.datetime.DateUtils
import java.text.ParseException
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

class RecurrencePeriod(@JvmField val until: RecurrenceUntil, @JvmField val params: String?) {

    fun stateToString(): String {
        return "${until.name}:${params}"
    }

    fun updateRRule(r: RRule, startDate: Calendar) {
        // ... (existing updateRRule for legacy compatibility)
    }

    fun toRRuleString(startDate: Calendar): String {
        val state = RecurrenceViewFactory.parseState(params)
        return when (until) {
            RecurrenceUntil.EXACTLY_TIMES -> {
                val count = state[RecurrenceViewFactory.P_COUNT]?.toIntOrNull() ?: 0
                ";COUNT=$count"
            }
            RecurrenceUntil.STOPS_ON_DATE -> {
                state[RecurrenceViewFactory.P_DATE]?.let { stopsOnDate ->
                    runCatching {
                        DateUtils.FORMAT_DATE_RFC_2445.parse(stopsOnDate)
                    }.getOrNull()?.let { date ->
                        val c = Calendar.getInstance().apply {
                            time = date
                            set(Calendar.HOUR_OF_DAY, startDate.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, startDate.get(Calendar.MINUTE))
                            set(Calendar.SECOND, startDate.get(Calendar.SECOND))
                            set(Calendar.MILLISECOND, 0)
                        }
                        ";UNTIL=${DateUtils.FORMAT_DATE_RFC_2445.format(c.time)}"
                    }
                }.orEmpty()
            }
            else -> ""
        }
    }

    companion object {
        @JvmStatic
        fun noEndDate(): RecurrencePeriod {
            return RecurrencePeriod(RecurrenceUntil.INDEFINITELY, null)
        }

        @JvmStatic
        fun empty(until: RecurrenceUntil): RecurrencePeriod {
            return RecurrencePeriod(until, null)
        }

        @JvmStatic
        fun parse(string: String): RecurrencePeriod {
            // fix for the typo in INDEFINETELY that is used in the database
            val a = string.replace("INDEFINETELY", "INDEFINITELY").split(":")
            return RecurrencePeriod(RecurrenceUntil.valueOf(a[0]), a[1])
        }

        @JvmStatic
        fun dateValueToDate(dvUtc: DateValue): Date {
            val c = GregorianCalendar()
            val dv = TimeUtils.fromUtc(dvUtc, c.timeZone)
            if (dv is TimeValue) {
                c.set(
                    dv.year(),
                    dv.month() - 1, // java.util's dates are zero-indexed
                    dv.day(),
                    dv.hour(),
                    dv.minute(),
                    dv.second()
                )
            } else {
                c.set(
                    dv.year(),
                    dv.month() - 1, // java.util's dates are zero-indexed
                    dv.day(),
                    0,
                    0,
                    0
                )
            }
            c.set(Calendar.MILLISECOND, 0)
            return c.time
        }

        @JvmStatic
        fun dateToDateValue(date: Date): DateValue {
            val c = GregorianCalendar()
            c.time = date
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            val s = c.get(Calendar.SECOND)
            return if (0 == h or m or s) {
                DateValueImpl(
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH)
                )
            } else {
                DateTimeValueImpl(
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH),
                    h,
                    m,
                    s
                )
            }
        }
    }
}
