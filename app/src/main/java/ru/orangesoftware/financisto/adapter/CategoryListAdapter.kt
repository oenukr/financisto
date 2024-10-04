package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.database.Cursor
import android.view.View
import android.widget.ResourceCursorAdapter
import android.widget.TextView

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.CategoryViewColumns
import ru.orangesoftware.financisto.model.Category

class CategoryListAdapter(
		private val db: DatabaseAdapter,
		context: Context,
		layout: Int,
		c: Cursor
) : ResourceCursorAdapter(context, layout, c) {
	
	private lateinit var attributes: Map<Long, String>
	
	fun fetchAttributes() {
		this.attributes = db.allAttributesMap
	}

	override fun bindView(view: View?, context: Context?, cursor: Cursor) {
		val id = cursor.getLong(CategoryViewColumns._id.ordinal)
		val level = cursor.getInt(CategoryViewColumns.level.ordinal)
		val title = cursor.getString(CategoryViewColumns.title.ordinal)
		val labelView = view?.findViewById<TextView>(android.R.id.text1)
		if (labelView != null) {
			labelView.text = Category.getTitle(title, level)
		} else {
			val spanView = view?.findViewById<TextView>(R.id.span)
			if (level > 1) {
				spanView?.visibility = View.VISIBLE
				spanView?.text = Category.getTitleSpan(level)
			} else {
				spanView?.visibility = View.GONE
			}
			val titleView = view?.findViewById<TextView>(R.id.line1)
			titleView?.text = title
			val attributesView = view?.findViewById<TextView>(R.id.label)
			if (::attributes.isInitialized && attributes.containsKey(id)) {
				attributesView?.visibility = View.VISIBLE
				attributesView?.text = attributes[id]
			} else {
				attributesView?.visibility = View.GONE
			}
		}
	}
}
