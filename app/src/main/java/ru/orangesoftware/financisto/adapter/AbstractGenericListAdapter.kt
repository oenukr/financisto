package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.view.View
import android.view.ViewGroup
import android.widget.ResourceCursorAdapter

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter

abstract class AbstractGenericListAdapter(
	db: DatabaseAdapter,
	context: Context,
	c: Cursor,
) : ResourceCursorAdapter(context, R.layout.generic_list_item, c) {

	override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
		val view = super.newView(context, cursor, parent)
		GenericViewHolder.createAndTag(view)
		return view
	}

	override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
		val views = view?.tag as? GenericViewHolder
		bindView(views, context, cursor)
	}

	protected abstract fun bindView(v: GenericViewHolder?, context: Context?, cursor: Cursor?)
}
