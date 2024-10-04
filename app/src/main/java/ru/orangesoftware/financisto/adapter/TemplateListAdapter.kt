package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.view.View

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter

class TemplateListAdapter(
	context: Context,
	db: DatabaseAdapter,
	c: Cursor
) : BlotterListAdapter(context, db, R.layout.blotter_list_item, c) {

	override fun isShowRunningBalance(): Boolean = false

	override fun bindView(view: View, context: Context, cursor: Cursor) {
		super.bindView(view, context, cursor)
	}
}
