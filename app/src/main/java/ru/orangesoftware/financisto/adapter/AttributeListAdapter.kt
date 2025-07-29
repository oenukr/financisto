package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.view.View

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Attribute

class AttributeListAdapter(
    db: DatabaseAdapter,
    context: Context,
    c: Cursor,
) : AbstractGenericListAdapter(db, context, c) {

    private val attributeTypes: Array<String> =
        context.resources.getStringArray(R.array.attribute_types)

    override fun bindView(viewHolder: GenericViewHolder?, context: Context?, cursor: Cursor?) {
        val attribute = cursor?.let(Attribute::fromCursor) ?: return

        viewHolder?.lineView?.text = attribute.title
        viewHolder?.numberView?.text = attributeTypes[attribute.type - 1]
        val defaultValue: String? = attribute.getTheDefaultValue()
        if (defaultValue != null) {
            viewHolder?.amountView?.visibility = View.VISIBLE
            viewHolder?.amountView?.text = defaultValue
        } else {
            viewHolder?.amountView?.visibility = View.GONE
        }
    }
}
