package ru.orangesoftware.financisto.adapter

import android.view.View
import android.widget.ImageView
import android.widget.TextView

import ru.orangesoftware.financisto.R

class GenericViewHolder {
    var lineView: TextView? = null
    var labelView: TextView? = null
    var numberView: TextView? = null
    var amountView: TextView? = null
    var iconView: ImageView? = null

    companion object {
        @JvmStatic
        fun createAndTag(view: View): GenericViewHolder = GenericViewHolder().apply {
            lineView = view.findViewById(R.id.line1)
            labelView = view.findViewById(R.id.label)
            numberView = view.findViewById(R.id.number)
            amountView = view.findViewById(R.id.date)
            iconView = view.findViewById(R.id.icon)
        }.also {
            view.tag = it
        }
    }
}
