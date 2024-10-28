package ru.orangesoftware.financisto.datetime

import android.content.Context
import android.provider.Settings

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

object DateUtils {
	@JvmStatic
	val FORMAT_TIMESTAMP_ISO_8601: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
	@JvmStatic
	val FORMAT_DATE_ISO_8601: DateFormat = SimpleDateFormat("yyyy-MM-dd")
	@JvmStatic
	val FORMAT_TIME_ISO_8601: DateFormat = SimpleDateFormat("HH:mm:ss")
	@JvmStatic
	val FORMAT_DATE_RFC_2445: DateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss")

	@JvmStatic
    fun getPeriod(period: PeriodType): Period = period.calculatePeriod()

	@JvmStatic
	fun startOfDay(c: Calendar): Calendar = c.apply {
		set(Calendar.HOUR_OF_DAY, 0)
		set(Calendar.MINUTE, 0)
		set(Calendar.SECOND, 0)
		set(Calendar.MILLISECOND, 0)
	}

	@JvmStatic
	fun endOfDay(c: Calendar): Calendar = c.apply {
		c.set(Calendar.HOUR_OF_DAY, 23)
		c.set(Calendar.MINUTE, 59)
		c.set(Calendar.SECOND, 59)
		c.set(Calendar.MILLISECOND, 999)
	}

	@JvmStatic
	fun atMidnight(date: Long): Long =
		startOfDay(Calendar.getInstance().apply { setTimeInMillis(date) }).getTimeInMillis()

	@JvmStatic
    fun atDayEnd(date: Long): Long =
        endOfDay(Calendar.getInstance().apply { setTimeInMillis(date) }).getTimeInMillis()

	@JvmStatic
	fun atDateAtTime(now: Long, startDate: Calendar): Date = Calendar.getInstance().apply {
		setTimeInMillis(now)
		set(Calendar.HOUR_OF_DAY, startDate.get(Calendar.HOUR_OF_DAY))
		set(Calendar.MINUTE, startDate.get(Calendar.MINUTE))
		set(Calendar.SECOND, startDate.get(Calendar.SECOND))
		set(Calendar.MILLISECOND, startDate.get(Calendar.MILLISECOND))
	}.time

	@JvmStatic
	fun getShortDateFormat(context: Context): DateFormat =
		android.text.format.DateFormat.getDateFormat(context)

	@JvmStatic
	fun getLongDateFormat(context: Context): DateFormat =
		android.text.format.DateFormat.getLongDateFormat(context)

	@JvmStatic
	fun getMediumDateFormat(context: Context): DateFormat =
		android.text.format.DateFormat.getMediumDateFormat(context)

	@JvmStatic
	fun getTimeFormat(context: Context): DateFormat =
		android.text.format.DateFormat.getTimeFormat(context)

	@JvmStatic
	fun is24HourFormat(context: Context): Boolean =
		"24" == Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)

	@JvmStatic
	fun zeroSeconds(dateTime: Calendar) {
		dateTime.set(Calendar.SECOND, 0)
		dateTime.set(Calendar.MILLISECOND, 0)
	}
}
