package ru.orangesoftware.financisto.activity

import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.view.View
import android.widget.ListAdapter

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.AttributeListAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.AttributeColumns
import ru.orangesoftware.financisto.utils.MenuItemInfo

class AttributeListActivity : AbstractListActivity(R.layout.attributes_list) {

	override fun createContextMenus(id: Long): MutableList<MenuItemInfo> =
		super.createContextMenus(id).apply { first { it.menuId == MENU_EDIT }.enabled = true }

	override fun addItem() {
		val intent = Intent(this, AttributeActivity::class.java)
		startActivityForResult(intent, 1)
	}

	override fun createAdapter(cursor: Cursor): ListAdapter = AttributeListAdapter(db, this, cursor)

	override fun createCursor(): Cursor = db.allAttributes

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (resultCode == RESULT_OK) {
			cursor.requery()
		}
	}

	override fun deleteItem(v: View?, position: Int, id: Long) {
		AlertDialog.Builder(this)
			.setTitle(R.string.delete)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setMessage(R.string.attribute_delete_alert)
			.setPositiveButton(R.string.delete) { _, _ ->
				db.deleteAttribute(id)
				cursor.requery()
			}
			.setNegativeButton(R.string.cancel, null)
			.show()
	}

	override fun editItem(v: View?, position: Int, id: Long) {
		val intent = Intent(this, AttributeActivity::class.java)
		intent.putExtra(AttributeColumns.ID, id)
		startActivityForResult(intent, 2)
	}

	override fun viewItem(v: View?, position: Int, id: Long) {
		editItem(v, position, id)
	}
}
