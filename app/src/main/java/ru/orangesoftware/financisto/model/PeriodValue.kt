package ru.orangesoftware.financisto.model

import java.util.Calendar

/**
 * Data that represents a result in a month.
 * @author Rodrigo Sousa
 *
 * @param month The month of reference.
 * @param value The result value in the given month.
 */
data class PeriodValue(
	/**
	 * The reference month.
	 */
	val month: Calendar?,
	/**
	 * The result value of the corresponding month.
	 */
	val value: Double,
) {
	/**
	 * @return The reference month in time milliseconds.
	 */
	fun getMonthTimeInMillis(): Long = month?.getTimeInMillis() ?: 0
}
