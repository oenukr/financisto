package ru.orangesoftware.financisto.repository.utils

import android.text.format.DateUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.toLocalDateTime
import ru.orangesoftware.financisto.repository.model.Period
import ru.orangesoftware.financisto.repository.model.PeriodType
import java.util.EnumSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val DAY_IN_MS = 24 * 60 * 60 * 1000L

private fun Instant.endOfDay(): Instant = this.plus(
	DateTimePeriod(hours = 23, minutes = 59, seconds = 59),
	TimeZone.currentSystemDefault(),
)

private fun Instant.plusWeek(): Instant = this
	.plus(6, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
	.endOfDay()

private fun Instant.plusMonth(): Instant = this
	.plus(1, DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
	.minus(1, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
	.endOfDay()

class RecurUtils {

	interface Layoutable {
		val layoutId: Int
	}
	
	enum class RecurInterval(
		override val layoutId: Int,
		override val titleId: Int,
	) : Layoutable, LocalizableEnum {
		NO_RECUR(0, R.string.recur_interval_no_recur),
		EVERY_X_DAY(R.layout.recur_every_x_day, R.string.recur_interval_every_x_day),
		DAILY(0, R.string.recur_interval_daily),
		WEEKLY(R.layout.recur_weekly, R.string.recur_interval_weekly){
			override fun next(startDate: Long): Period {
				val datetimeInSystemZone: LocalDateTime = Instant.fromEpochMilliseconds(startDate)
					.toLocalDateTime(TimeZone.currentSystemDefault())
				val begin: Instant = datetimeInSystemZone.date.atStartOfDayIn(TimeZone.currentSystemDefault())
				val end: Instant = begin.plusWeek()
				return Period(
					PeriodType.CUSTOM,
					begin.toEpochMilliseconds(),
					end.toEpochMilliseconds(),
				)
			}
		},		
		MONTHLY(0, R.string.recur_interval_monthly){
			override fun next(startDate: Long): Period {
				val datetimeInSystemZone: LocalDateTime = Instant.fromEpochMilliseconds(startDate)
					.toLocalDateTime(TimeZone.currentSystemDefault())
				val begin: Instant = datetimeInSystemZone.date.atStartOfDayIn(TimeZone.currentSystemDefault())
				val end: Instant = begin.plusMonth()
				return Period(
					PeriodType.CUSTOM,
					begin.toEpochMilliseconds(),
					end.toEpochMilliseconds(),
				)
			}
		},
		SEMI_MONTHLY(R.layout.recur_semi_monthly, R.string.recur_interval_semi_monthly),
		YEARLY(0, R.string.recur_interval_yearly);		


		open fun next(startDate: Long): Period {
			throw UnsupportedOperationException()
		}
	}

	enum class RecurPeriod(
		override val layoutId: Int,
		override val titleId: Int,
	) : Layoutable, LocalizableEnum {
		STOPS_ON_DATE(R.layout.recur_stops_on_date, R.string.recur_stops_on_date){
			override fun toSummary(context: Context, param: Long) =
				String.format(
					context.getString(R.string.recur_stops_on_date_summary),
					Instant.fromEpochMilliseconds(param)
						.toLocalDateTime(TimeZone.currentSystemDefault())
						.format(LocalDateTime.Formats.ISO),
				)

			override fun repeat(interval: RecurInterval, startDate: Long, periodParam: Long): Array<Period> {
				var endDate = 0L
				var startDate = startDate
				var periods = mutableListOf<Period>()
				while (endDate < periodParam) {
					val period = interval.next(startDate)
					startDate = period.end + 1
					endDate = period.end
					periods.add(period)
				}
				return periods.toTypedArray()
			}
		},
//		INDEFINITELY(0, R.string.recur_indefinitely){
//			@Override
//			public String toSummary(Context context, long param) {
//				return context.getString(R.string.recur_indefinitely);
//			}
//		},
		EXACTLY_TIMES(R.layout.recur_exactly_n_times, R.string.recur_exactly_n_times){
			override fun toSummary(context: Context, param: Long) = String.format(
				context.getString(R.string.recur_exactly_n_times_summary),
				param,
			)

			override fun repeat(interval: RecurInterval, startDate: Long, periodParam: Long): Array<Period> {
				var periods = mutableListOf<Period>()
				var startDate = startDate
				repeat(periodParam.toInt()) {
					val period = interval.next(startDate)
					startDate = period.end + 1
					periods.add(period)
				}
				return periods.toTypedArray()
			}
		};

		abstract fun toSummary(context: Context, param: Long): String

		abstract fun repeat(interval: RecurInterval, startDate: Long, periodParam: Long): Array<Period>
	
	}

	abstract class Recur @JvmOverloads constructor(
		val interval: RecurInterval,
		values: Map<String, String>? = null,
	) : Cloneable {
		var startDate: Long = values?.get("startDate")?.toLong() ?: Clock.System.now().toEpochMilliseconds()
		var period: RecurPeriod = values?.get("period")?.let(RecurPeriod::valueOf) ?: RecurPeriod.EXACTLY_TIMES
		var periodParam: Long = values?.get("periodParam")?.toLong() ?: 1

		override fun toString(): String = toString(
			arrayOf(
				interval.name,
				"startDate=$startDate",
				"period=$period",
				"periodParam=$periodParam",
			).joinToString(separator = ",")
		)

		override fun clone(): Recur {
			try {
				return super.clone() as Recur
			} catch (e: CloneNotSupportedException) {
				throw RuntimeException(e)
			}
		}

		fun toString(context: Context) {
			val dateFormat: DateTimeFormat = DateTimeFormat<Long>(DateUtils.getShortDateFormat(context).toPattern())
			return "${context.getString(R.string.recur_repeat_starts_on)} ${dateFormat.format(Instant.fromEpochMilliseconds(startDate))}, ${context.getString(interval.titleId)}, ${period.toSummary(context, periodParam)}"
		}

		open fun toString(string: String): String {
			return string
		}

		//public abstract long getNextRecur(long currentDate);

	}

	class NoRecur @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.NO_RECUR, values)

	class EveryXDay @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.EVERY_X_DAY, values) {
		
		val days: Int = values?.get("days")?.toInt() ?: 0

		override fun toString(string: String): String {
			return "${string}days=$days"
		}
		
		//@Override
		fun getNextRecur(currentDate: Long): Long {
			if (currentDate <= startDate) {
				return startDate
			}
			val now: Instant = Instant.fromEpochMilliseconds(currentDate)
			val period: Duration = days.days
			val delta: Duration = now - Instant.fromEpochMilliseconds(startDate)
			val n: Double = delta / period
			val next: Instant = now.plus(period.times(n))
			return if (next > now) {
				next
			} else {
				next + period
			}.toEpochMilliseconds()
		}
				
	}
	
	class Daily @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.DAILY, values)

	enum class DayOfWeek(val checkboxId: Int) {
		SUN(R.id.daySun), 
		MON(R.id.dayMon), 
		TUE(R.id.dayTue), 
		WED(R.id.dayWed), 
		THR(R.id.dayThr), 
		FRI(R.id.dayFri), 
		SAT(R.id.daySat);
	}
	
	class Weekly @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.WEEKLY, values) {

		private val days: MutableSet<DayOfWeek> by lazy {
			val days = values?.get("days") ?: return@lazy EnumSet.allOf(DayOfWeek::class.java).apply {
                remove(DayOfWeek.SAT)
                remove(DayOfWeek.SUN)
            }

			days.split(",")
				.map { DayOfWeek.valueOf(it) }
				.toSet()
				.ifEmpty { EnumSet.noneOf(DayOfWeek::class.java) }
		}

		override fun toString(string: String): String {
			return "${string}days=${days.joinToString(separator = ",") { it.name }}"
		}

		fun isSet(d: DayOfWeek): Boolean {
			return days.contains(d)
		}
		
		fun set(d: DayOfWeek) {
			days.add(d)
		}
				
		fun unset(d: DayOfWeek) {
			days.remove(d)
		}
	}

	class SemiMonthly @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.SEMI_MONTHLY, values) {

		val firstDay: Int = values?.get("firstDay")?.toInt() ?: 15
		val secondDay: Int = values?.get("secondDay")?.toInt() ?: 30
		
		override fun toString(string: String): String {
			return "firstDay=${firstDay},secondDay=${secondDay},"
		}
		
	}
 
	class Monthly @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.MONTHLY, values)

	class Yearly @JvmOverloads constructor(
		values: Map<String, String>? = null,
	) : Recur(RecurInterval.YEARLY, values)

	companion object {
		@JvmStatic
		fun createFromExtraString(extra: String): Recur? {
			if (extra.isEmpty()) return NoRecur()

			val recurrences: List<String> = extra.split(",")
			val interval: RecurInterval = RecurInterval.valueOf(recurrences[0])
			val values: Map<String, String> = toMap(recurrences)
			return when (interval) {
				RecurInterval.NO_RECUR -> NoRecur(values)
				RecurInterval.EVERY_X_DAY -> EveryXDay(values)
				RecurInterval.DAILY -> Daily(values)
				RecurInterval.WEEKLY -> Weekly(values)
				RecurInterval.SEMI_MONTHLY -> SemiMonthly(values)
				RecurInterval.MONTHLY -> Monthly(values)
				RecurInterval.YEARLY -> Yearly(values)
				else -> null
			}
		}

		@JvmStatic
		fun createRecur(interval: RecurInterval): Recur? = when (interval) {
			RecurInterval.NO_RECUR -> NoRecur()
			RecurInterval.EVERY_X_DAY -> EveryXDay()
			RecurInterval.DAILY -> Daily()
			RecurInterval.WEEKLY -> Weekly()
			RecurInterval.SEMI_MONTHLY -> SemiMonthly()
			RecurInterval.MONTHLY -> Monthly()
			RecurInterval.YEARLY -> Yearly()
			else -> null
		}

		private fun toMap(recurrences: List<String>): Map<String, String> {
			val map: MutableMap<String, String> = mutableMapOf()
			recurrences.forEach {
				val keyValue = it.split("=")
				if (keyValue.size > 1) {
					map.put(keyValue[0], keyValue[1])
				}
			}
			return map
		}

		private fun getLong(values: Map<String, String>, string: String): Long? {
			return values.get(string)?.toLong()
		}

		private fun getInt(values: Map<String, String>, string: String): Int? {
			return values.get(string)?.toInt()
		}

		@JvmStatic
		fun createDefaultRecur(): Recur {
			val now: Instant = Clock.System.now()
			val timeZone = TimeZone.currentSystemDefault()
			val recur: NoRecur = NoRecur()
			recur.startDate = now
				.toLocalDateTime(timeZone)
				.date
				.atStartOfDayIn(timeZone).toEpochMilliseconds()
			recur.period = RecurPeriod.STOPS_ON_DATE
			recur.periodParam = now
				.plus(1, DateTimeUnit.MONTH, timeZone)
				.toEpochMilliseconds()
			return recur
		}

		@JvmStatic
		fun periods(recur: Recur): Array<Period> {
			val interval: RecurInterval = recur.interval
			val period: RecurPeriod = recur.period
			return if (interval == RecurInterval.NO_RECUR) {
				if (period != RecurPeriod.STOPS_ON_DATE) {
					arrayOf(PeriodType.THIS_MONTH.calculatePeriod())
				}
				arrayOf(Period(PeriodType.CUSTOM, recur.startDate, recur.periodParam))
			} else {
				period.repeat(interval, recur.startDate, recur.periodParam)
			}
		}
	}

}
