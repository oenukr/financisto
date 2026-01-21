package ru.orangesoftware.financisto.recur

import android.content.Context
import com.google.ical.values.RRule
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.utils.Logger
import java.text.ParseException
import java.util.*

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
        return DateUtils.FORMAT_TIMESTAMP_ISO_8601.format(startDate!!.time) + "~" +
                pattern!!.stateToString() + "~" +
                period!!.stateToString()
    }

    fun getStartDate(): Calendar? {
        return startDate
    }

    fun updateStartDate(y: Int, m: Int, d: Int) {
        startDate!!.set(Calendar.YEAR, y)
        startDate!!.set(Calendar.MONTH, m)
        startDate!!.set(Calendar.DAY_OF_MONTH, d)
    }

    fun updateStartTime(h: Int, m: Int, s: Int) {
        startDate!!.set(Calendar.HOUR_OF_DAY, h)
        startDate!!.set(Calendar.MINUTE, m)
        startDate!!.set(Calendar.SECOND, s)
        startDate!!.set(Calendar.MILLISECOND, 0)
    }

    fun generateDates(start: Date, end: Date): List<Date> {
        val ri = createIterator(start)
        val dates = ArrayList<Date>()
        while (ri.hasNext()) {
            val nextDate = ri.next()
            if (nextDate != null) {
                if (nextDate.after(end)) {
                    break
                }
                dates.add(nextDate)
            }
        }
        return dates
    }

    fun createIterator(nowDate: Date): DateRecurrenceIterator {
        var now = nowDate
        val rrule = createRRule()
        return try {
            logger.d("Creating iterator for " + rrule.toIcal())
            if (now.before(startDate!!.time)) {
                now = startDate!!.time
            }
            val c = Calendar.getInstance()
            c.time = startDate!!.time
            //c.set(Calendar.HOUR_OF_DAY, startDate.get(Calendar.HOUR_OF_DAY));
            //c.set(Calendar.MINUTE, startDate.get(Calendar.MINUTE));
            //c.set(Calendar.SECOND, startDate.get(Calendar.SECOND));
            c.set(Calendar.MILLISECOND, 0)
            DateRecurrenceIterator.create(rrule, now, c.time)
        } catch (e: ParseException) {
            logger.w("Unable to create iterator for " + rrule.toIcal())
            DateRecurrenceIterator.empty()
        }
    }

    private fun createRRule(): RRule {
        return if (pattern!!.frequency == RecurrenceFrequency.GEEKY) {
            try {
                val map = RecurrenceViewFactory.parseState(pattern!!.params)
                val rrule = map[RecurrenceViewFactory.P_INTERVAL]
                RRule("RRULE:" + rrule!!.uppercase(Locale.getDefault()))
            } catch (e: ParseException) {
                throw IllegalArgumentException(pattern!!.params)
            }
        } else {
            val r = RRule()
            pattern!!.updateRRule(r)
            period!!.updateRRule(r, startDate!!)
            r
        }
    }

    fun toInfoString(context: Context): String {
        return (context.getString(pattern!!.frequency.titleId) +
                ", " + context.getString(R.string.recur_repeat_starts_on) + ": " +
                DateUtils.getShortDateFormat(context).format(startDate!!.time) + " " +
                DateUtils.getTimeFormat(context).format(startDate!!.time))
    }

    companion object {
        @JvmStatic
        fun parse(recurrence: String): Recurrence {
            val r = Recurrence()
            val a = recurrence.split("~".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            try {
                val d = DateUtils.FORMAT_TIMESTAMP_ISO_8601.parse(a[0])
                val c = Calendar.getInstance()
                c.time = d!!
                r.startDate = c
            } catch (e: ParseException) {
                throw RuntimeException(recurrence)
            }
            r.pattern = RecurrencePattern.parse(a[1])
            r.period = RecurrencePeriod.parse(a[2])
            return r
        }

        @JvmStatic
        fun noRecur(): Recurrence {
            val r = Recurrence()
            r.startDate = Calendar.getInstance()
            r.pattern = RecurrencePattern.noRecur()
            r.period = RecurrencePeriod.noEndDate()
            return r
        }
    }
}
