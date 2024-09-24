package ru.orangesoftware.financisto.activity

import android.content.Context
import android.widget.ImageButton

import androidx.core.content.ContextCompat

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.filter.WhereFilter

object FilterState {
    @JvmStatic
    fun updateFilterColor(context: Context, filter: WhereFilter, button: ImageButton) {
        val color = if (filter.isEmpty) {
            ContextCompat.getColor(context, R.color.bottom_bar_tint)
        } else {
            ContextCompat.getColor(context, R.color.holo_blue_dark)
        }
        button.setColorFilter(color)
    }
}
