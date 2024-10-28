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

    override fun bindView(v: GenericViewHolder?, context: Context?, cursor: Cursor?) {
        val a = Attribute.fromCursor(cursor)
        v?.lineView?.text = a.title
        v?.numberView?.text = attributeTypes[a.type - 1]
        val defaultValue: String? = a.getDefaultValue()
        if (defaultValue != null) {
            v?.amountView?.visibility = View.VISIBLE
            v?.amountView?.text = defaultValue
        } else {
            v?.amountView?.visibility = View.GONE
        }
    }
}
