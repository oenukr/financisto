package ru.orangesoftware.financisto.activity

import android.content.Context

import androidx.core.content.ContextCompat

import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog

import ru.orangesoftware.financisto.R

object UiUtils {

    fun applyTheme(context: Context, dialog: DatePickerDialog) {
        dialog.accentColor = ContextCompat.getColor(context, R.color.colorPrimary)
        dialog.setThemeDark(true)
    }

    fun applyTheme(context: Context, dialog: TimePickerDialog) {
        dialog.accentColor = ContextCompat.getColor(context, R.color.colorPrimary)
        dialog.setThemeDark(true)
    }

}
