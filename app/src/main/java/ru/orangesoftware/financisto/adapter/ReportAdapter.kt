package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import ru.orangesoftware.financisto.graph.GraphUnit
import ru.orangesoftware.financisto.graph.GraphWidget
import kotlin.math.abs

class ReportAdapter(
    private val context: Context,
    private val units: List<GraphUnit>,
) : BaseAdapter() {

    private var maxAmount: Long = 0
    private var maxAmountWidth: Long = 0

    init {
        val rect = Rect()
        units.forEach { unit ->
            unit.forEach { ammount ->
                val amountText = ammount.getAmountText()
                unit.style.amountPaint.getTextBounds(amountText, 0, amountText.length, rect)
                ammount.amountTextWidth = rect.width()
                ammount.amountTextHeight = rect.height()
                maxAmount = maxAmount.coerceAtLeast(abs(ammount.amount))
                maxAmountWidth = maxAmountWidth.coerceAtLeast(ammount.amountTextWidth.toLong())
            }
        }
    }

    override fun getCount(): Int = units.size

    override fun getItem(position: Int): Any = units[position]

    override fun getItemId(position: Int): Long = units[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        GraphWidget(context, units[position], maxAmount, maxAmountWidth).apply {
            setPadding(5, 10, 5, 5)
        }
}
