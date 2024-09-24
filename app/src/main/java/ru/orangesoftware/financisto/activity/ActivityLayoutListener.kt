package ru.orangesoftware.financisto.activity

import android.view.View.OnClickListener

import ru.orangesoftware.financisto.model.MultiChoiceItem

interface ActivityLayoutListener : OnClickListener {

	fun onSelectedPos(id: Int, selectedPos: Int)
	fun onSelectedId(id: Int, selectedId: Long)
	fun onSelected(id: Int, items: List<MultiChoiceItem>)
	
}
