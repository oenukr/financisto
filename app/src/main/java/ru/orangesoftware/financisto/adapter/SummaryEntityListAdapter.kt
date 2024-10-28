package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

import androidx.core.content.ContextCompat

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.SummaryEntityEnum

class SummaryEntityListAdapter(
    private val context: Context,
    private val entities: Array<SummaryEntityEnum>
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = entities.size

    override fun getItem(position: Int): Any = entities[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.summary_entity_list_item, parent, false)
        val h = if (convertView == null) {
            Holder().apply {
                icon = view.findViewById(R.id.icon)
                title = view.findViewById(R.id.line1)
                label = view.findViewById(R.id.label)
            }.also { view.tag = it }
        } else {
            convertView.tag as Holder
        }
        val r = entities[position]
        h.title?.setText(r.titleId)
        h.label?.setText(r.getSummaryId())
        if (r.iconId > 0) {
            h.icon?.setImageResource(r.iconId)
            h.icon?.setColorFilter(ContextCompat.getColor(context, R.color.colorPrimary))
        }
        return view
    }

    private data class Holder(
        var icon: ImageView? = null,
        var title: TextView? = null,
        var label: TextView? = null,
    )
}
