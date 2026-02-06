package ru.orangesoftware.financisto.recur

import com.google.ical.values.RRule
import com.google.ical.values.Weekday
import ru.orangesoftware.financisto.recur.RecurrenceViewFactory.DayOfWeek
import ru.orangesoftware.financisto.recur.RecurrenceViewFactory.MonthlyPattern
import ru.orangesoftware.financisto.recur.RecurrenceViewFactory.SpecificDayPostfix
import ru.orangesoftware.financisto.recur.RecurrenceViewFactory.SpecificDayPrefix

class RecurrencePattern(@JvmField val frequency: RecurrenceFrequency, @JvmField val params: String?) {

    fun updateRRule(r: RRule) {
        // ... (existing updateRRule for legacy compatibility)
    }

    fun toRRuleString(): String {
        val state = RecurrenceViewFactory.parseState(params)
        val interval = state[RecurrenceViewFactory.P_INTERVAL]?.toIntOrNull() ?: 1
        val sb = StringBuilder()
        when (frequency) {
            RecurrenceFrequency.DAILY -> {
                sb.append("FREQ=DAILY;INTERVAL=$interval")
            }
            RecurrenceFrequency.WEEKLY -> {
                sb.append("FREQ=WEEKLY;INTERVAL=$interval")
                state[RecurrenceViewFactory.P_DAYS]?.let { days ->
                    val a = days.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val rfcDays = a.joinToString(",") { DayOfWeek.valueOf(it).rfcName }
                    sb.append(";BYDAY=$rfcDays")
                }
            }
            RecurrenceFrequency.MONTHLY -> {
                sb.append("FREQ=MONTHLY;INTERVAL=$interval")
                state[RecurrenceViewFactory.P_MONTHLY_PATTERN + "_0"]?.let { monthlyPatternParam ->
                    val pattern = MonthlyPattern.valueOf(monthlyPatternParam)
                    when (pattern) {
                        MonthlyPattern.EVERY_NTH_DAY -> {
                            val everyNthDay = state[RecurrenceViewFactory.P_MONTHLY_PATTERN_PARAMS + "_0"]?.toIntOrNull() ?: 1
                            sb.append(";BYMONTHDAY=$everyNthDay")
                        }
                        MonthlyPattern.SPECIFIC_DAY -> {
                            state[RecurrenceViewFactory.P_MONTHLY_PATTERN_PARAMS + "_0"]?.let { s ->
                                val x = s.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                if (x.size >= 2) {
                                    val prefix = SpecificDayPrefix.valueOf(x[0])
                                    val postfix = SpecificDayPostfix.valueOf(x[1])
                                    val num = if (prefix == SpecificDayPrefix.LAST) -1 else prefix.ordinal + 1
                                    when (postfix) {
                                        SpecificDayPostfix.DAY -> sb.append(";BYMONTHDAY=$num")
                                        SpecificDayPostfix.WEEKDAY -> {
                                            sb.append(";BYDAY=MO,TU,WE,TH,FR;BYSETPOS=$num")
                                        }
                                        SpecificDayPostfix.WEEKEND_DAY -> {
                                            sb.append(";BYDAY=SA,SU;BYSETPOS=$num")
                                        }
                                        else -> {
                                            //su-sa
                                            val day = Weekday.values()[postfix.ordinal - 3].name
                                            sb.append(";BYDAY=$num$day")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        return sb.toString()
    }

    fun stateToString(): String {
        return "${frequency.name}:${params}"
    }

    companion object {
        @JvmStatic
        fun parse(recurrencePattern: String): RecurrencePattern {
            // fix for the typo in INDEFINETELY that is used in the database
            val a = recurrencePattern.replace("INDEFINETELY", "INDEFINITELY").split(":")
            if (a.size < 2) {
                return noRecur()
            }
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
