package ru.orangesoftware.financisto.adapter

import android.content.Context
import android.widget.ArrayAdapter
import ru.orangesoftware.financisto.model.MyEntity

class MyEntityAdapter<T : MyEntity> @JvmOverloads constructor(
	context: Context,
	resource: Int = 0,
	textViewResourceId: Int,
	objects: List<T> = emptyList(),
) : ArrayAdapter<T>(
	context,
	resource,
	textViewResourceId,
	objects,
) {
	override fun getItemId(position: Int): Long = getItem(position)?.id ?: 0L
}
