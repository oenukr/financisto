package ru.orangesoftware.financisto.recur

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.LocalizableEnum

enum class RecurrenceFrequency(override val titleId: Int) : LocalizableEnum {
	
	NO_RECUR(R.string.recur_interval_no_recur),
	DAILY(R.string.recur_interval_daily),
	WEEKLY(R.string.recur_interval_weekly),		
	MONTHLY(R.string.recur_interval_monthly),
	//SEMI_MONTHLY(R.string.recur_interval_semi_monthly),
	GEEKY(R.string.recur_interval_geeky);
	//YEARLY(R.string.recur_interval_yearly);		
}
