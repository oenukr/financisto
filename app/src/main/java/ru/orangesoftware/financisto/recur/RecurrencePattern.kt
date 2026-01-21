package ru.orangesoftware.financisto.recur

import com.google.ical.values.Frequency
import com.google.ical.values.RRule
import com.google.ical.values.Weekday
import com.google.ical.values.WeekdayNum
import ru.orangesoftware.financisto.recur.RecurrenceViewFactory.*
import java.util.*

class RecurrencePattern(@JvmField val frequency: RecurrenceFrequency, @JvmField val params: String?) {

    fun updateRRule(r: RRule) {
        val state = RecurrenceViewFactory.parseState(params)
        val interval = state[RecurrenceViewFactory.P_INTERVAL]?.toInt() ?: 1
        r.interval = interval
        when (frequency) {
            RecurrenceFrequency.DAILY -> {
                r.freq = Frequency.DAILY
            }
            RecurrenceFrequency.WEEKLY -> {
                r.freq = Frequency.WEEKLY
                val byDay = LinkedList<WeekdayNum>()
                val days = state[RecurrenceViewFactory.P_DAYS]
                if (days != null) {
                    val a = days.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (s in a) {
                        val d = DayOfWeek.valueOf(s)
                        byDay.add(WeekdayNum(0, Weekday.valueOf(d.rfcName)))
                    }
                }
                r.byDay = byDay
            }
            RecurrenceFrequency.MONTHLY -> {
                r.freq = Frequency.MONTHLY
                val monthlyPatternParam = state[RecurrenceViewFactory.P_MONTHLY_PATTERN + "_0"]
                if (monthlyPatternParam != null) {
                    val pattern = MonthlyPattern.valueOf(monthlyPatternParam)
                    when (pattern) {
                        MonthlyPattern.EVERY_NTH_DAY -> {
                            val everyNthDay = state[RecurrenceViewFactory.P_MONTHLY_PATTERN_PARAMS + "_0"]?.toInt() ?: 1
                            r.byMonthDay = intArrayOf(everyNthDay)
                        }
                        MonthlyPattern.SPECIFIC_DAY -> {
                            val s = state[RecurrenceViewFactory.P_MONTHLY_PATTERN_PARAMS + "_0"]
                            if (s != null) {
                                val x = s.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                val prefix = SpecificDayPrefix.valueOf(x[0])
                                val postfix = SpecificDayPostfix.valueOf(x[1])
                                val num = if (prefix == SpecificDayPrefix.LAST) -1 else prefix.ordinal + 1
                                when (postfix) {
                                    SpecificDayPostfix.DAY -> r.byMonthDay = intArrayOf(num)
                                    SpecificDayPostfix.WEEKDAY -> {
                                        r.byDay = WEEKDAYS
                                        r.bySetPos = intArrayOf(num)
                                    }
                                    SpecificDayPostfix.WEEKEND_DAY -> {
                                        r.byDay = WEEKENDS
                                        r.bySetPos = intArrayOf(num)
                                    }
                                    else -> {
                                        //su-sa
                                        val day = Weekday.values()[postfix.ordinal - 3]
                                        r.byDay = listOf(WeekdayNum(num, day))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    fun stateToString(): String {
        return "${frequency.name}:${params}"
    }

    companion object {
        private val WEEKDAYS = LinkedList<WeekdayNum>()
        private val WEEKENDS = LinkedList<WeekdayNum>()

        init {
            WEEKDAYS.add(WeekdayNum(0, Weekday.MO))
            WEEKDAYS.add(WeekdayNum(0, Weekday.TU))
            WEEKDAYS.add(WeekdayNum(0, Weekday.WE))
            WEEKDAYS.add(WeekdayNum(0, Weekday.TH))
            WEEKDAYS.add(WeekdayNum(0, Weekday.FR))
            WEEKENDS.add(WeekdayNum(0, Weekday.SU))
            WEEKENDS.add(WeekdayNum(0, Weekday.SA))
        }

        @JvmStatic
        fun parse(recurrencePattern: String): RecurrencePattern {
            // fix for the typo in INDEFINETELY that is used in the database
            val a = recurrencePattern.replace("INDEFINETELY", "INDEFINITELY").split(":")
            return RecurrencePattern(RecurrenceFrequency.valueOf(a[0]), a[1])
        }

        @JvmStatic
        fun noRecur(): RecurrencePattern {
            return RecurrencePattern(RecurrenceFrequency.NO_RECUR, null)
        }

        @JvmStatic
        fun empty(frequency: RecurrenceFrequency): RecurrencePattern {
            return RecurrencePattern(frequency, null)
        }
    }
}
