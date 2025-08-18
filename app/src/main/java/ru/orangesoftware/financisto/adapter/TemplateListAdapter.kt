package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.view.View
import ru.orangesoftware.financisto.R

class TemplateListAdapter(
	context: Context,
	c: Cursor,
) : BlotterListAdapter(context, R.layout.blotter_list_item, c) {

	override fun isShowRunningBalance(): Boolean = false

	override fun bindView(view: View, context: Context, cursor: Cursor) {
		super.bindView(view, context, cursor)
	}
}
