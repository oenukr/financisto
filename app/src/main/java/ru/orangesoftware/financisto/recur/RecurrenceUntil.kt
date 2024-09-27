package ru.orangesoftware.financisto.recur

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.LocalizableEnum

enum class RecurrenceUntil(private val titleId: Int) : LocalizableEnum {

	INDEFINITELY(R.string.recur_indefinitely),
	EXACTLY_TIMES(R.string.recur_exactly_n_times),
	STOPS_ON_DATE(R.string.recur_stops_on_date);

	override fun getTitleId(): Int = titleId
	override fun getName(): String = name
}
