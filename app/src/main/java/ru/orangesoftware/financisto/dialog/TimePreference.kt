package ru.orangesoftware.financisto.dialog

import android.content.Context
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.TimePicker
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.datetime.DateUtils.is24HourFormat

private const val DEFAULT_VALUE: Int = 600

class TimePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : DialogPreference(context, attrs, defStyle), TimePicker.OnTimeChangedListener {

    init {
        isPersistent = true
    }

    private var hh: Int = 0
    private var mm: Int = 0

    @Deprecated("Deprecated in Java")
    override fun onCreateDialogView(): View = TimePicker(context).apply {
        setIs24HourView(is24HourFormat(context))
        setOnTimeChangedListener(this@TimePreference)
        currentHour = getHour()
        currentMinute = getMinute()
    }

    @Deprecated("Deprecated in Java")
    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (!positiveResult) {
            return
        }
        if (shouldPersist()) {
            persistInt(100 * hh + mm)
        }
        notifyChanged()
    }

    private fun getHour(): Int = getPersistedInt(DEFAULT_VALUE) / 100

    private fun getMinute(): Int {
        val hm: Int = getPersistedInt(DEFAULT_VALUE)
        val h = hm / 100
        return hm - 100 * h
    }

    override fun onTimeChanged(timePicker: TimePicker, hh: Int, mm: Int) {
        this.hh = hh
        this.mm = mm
    }

    @Deprecated("Deprecated in Java")
    override fun getSummary(): CharSequence =
        context.getString(R.string.auto_backup_time_summary, getHour(), getMinute())
}
