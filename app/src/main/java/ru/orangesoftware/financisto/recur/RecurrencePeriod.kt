package ru.orangesoftware.financisto.recur

import ru.orangesoftware.financisto.datetime.DateUtils
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

class RecurrencePeriod(@JvmField val until: RecurrenceUntil, @JvmField val params: String?) {

    fun stateToString(): String {
        return "${until.name}:${params}"
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
                    }.getOrElse {
                        throw IllegalArgumentException("Unable to parse stopsOnDate: $stopsOnDate", it)
                    }?.let { date ->
                        val c = Calendar.getInstance().apply { time = date }
                        
                        val year = c.get(Calendar.YEAR)
                        val month = c.get(Calendar.MONTH) + 1
                        val day = c.get(Calendar.DAY_OF_MONTH)
                        
                        val hour = startDate.get(Calendar.HOUR_OF_DAY)
                        val minute = startDate.get(Calendar.MINUTE)
                        val second = startDate.get(Calendar.SECOND)
                        
                        val untilString = "%04d%02d%02dT%02d%02d%02dZ".format(
                            year, month, day,
                            hour, minute, second
                        )
                        ";UNTIL=$untilString"
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
            if (a.size < 2) {
                return noEndDate()
            }
            return runCatching {
                RecurrencePeriod(RecurrenceUntil.valueOf(a[0]), a[1])
            }.getOrElse {
                noEndDate()
            }
        }
    }
}
