package ru.orangesoftware.financisto.recur

import android.content.Context
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.utils.Logger
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Recurrence {
    private val logger: Logger = DependenciesHolder().logger

    // TODO ds: replace with time holder
    // only HH:mm:ss should be used in RRULE, not the date part
    private var startDate: Calendar? = null

    @JvmField
    var pattern: RecurrencePattern? = null

    @JvmField
    var period: RecurrencePeriod? = null

    fun stateToString(): String {
        val startStr = startDate?.let { DateUtils.FORMAT_TIMESTAMP_ISO_8601.format(it.time) }.orEmpty()
        val patternStr = pattern?.stateToString().orEmpty()
        val periodStr = period?.stateToString().orEmpty()
        return "$startStr~$patternStr~$periodStr"
    }

    fun getStartDate(): Calendar? {
        return startDate
    }

    fun updateStartDate(y: Int, m: Int, d: Int) {
        val date = startDate ?: Calendar.getInstance().also { startDate = it }
        date.set(Calendar.YEAR, y)
        date.set(Calendar.MONTH, m)
        date.set(Calendar.DAY_OF_MONTH, d)
    }

    fun updateStartTime(h: Int, m: Int, s: Int) {
        val date = startDate ?: Calendar.getInstance().also { startDate = it }
        date.set(Calendar.HOUR_OF_DAY, h)
        date.set(Calendar.MINUTE, m)
        date.set(Calendar.SECOND, s)
        date.set(Calendar.MILLISECOND, 0)
    }

    fun generateDates(start: Date, end: Date): List<Date> {
        val ri = createIterator(start)
        val dates = mutableListOf<Date>()
        while (ri.hasNext()) {
            ri.next()?.let { nextDate ->
                if (nextDate.after(end)) {
                    return dates
                }
                dates.add(nextDate)
            } ?: break
        }
        return dates
    }

    fun createIterator(nowDate: Date): DateRecurrenceIterator {
        return runCatching {
            val rruleString = createRRuleString()
            logger.d("Creating iterator for $rruleString")
            startDate?.let { start ->
                var now = nowDate
                if (now.before(start.time)) {
                    now = start.time
                }
                DateRecurrenceIterator.create(rruleString, now, start.time)
            } ?: DateRecurrenceIterator.empty()
        }.getOrElse {
            logger.w(it, "Unable to create iterator")
            DateRecurrenceIterator.empty()
        }
    }

    private fun createRRuleString(): String {
        return pattern?.let { pat ->
            if (pat.frequency == RecurrenceFrequency.GEEKY) {
                runCatching {
                    val map = RecurrenceViewFactory.parseState(pat.params)
                    map[RecurrenceViewFactory.P_INTERVAL]?.uppercase(Locale.ROOT)
                }.getOrNull()
            } else {
                startDate?.let { start ->
                    pat.toRRuleString() + (period?.toRRuleString(start) ?: "")
                }
            }
        }.orEmpty()
    }

    fun toInfoString(context: Context): String {
        return startDate?.let { start ->
            pattern?.let { pat ->
                context.getString(pat.frequency.titleId) +
                        ", " + context.getString(R.string.recur_repeat_starts_on) + ": " +
                        DateUtils.getShortDateFormat(context).format(start.time) + " " +
                        DateUtils.getTimeFormat(context).format(start.time)
            }
        }.orEmpty()
    }

    companion object {
        @JvmStatic
        fun parse(recurrence: String): Recurrence {
            val r = Recurrence()
            val a = recurrence.split("~".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (a.size < 3) {
                return noRecur()
            }
            runCatching {
                val d = DateUtils.FORMAT_TIMESTAMP_ISO_8601.parse(a[0])
                r.startDate = Calendar.getInstance().apply {
                    time = d ?: Date()
                }
            }.onFailure {
                throw RuntimeException(recurrence)
            }
            r.pattern = RecurrencePattern.parse(a[1])
            r.period = RecurrencePeriod.parse(a[2])
            return r
        }

        @JvmStatic
        fun noRecur(): Recurrence {
            return Recurrence().apply {
                startDate = Calendar.getInstance()
                pattern = RecurrencePattern.noRecur()
                period = RecurrencePeriod.noEndDate()
            }
        }
    }
}
