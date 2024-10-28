package ru.orangesoftware.financisto.model

interface MultiChoiceItem {
	fun getId(): Long
	fun getTitle(): String
	fun isChecked(): Boolean
	fun setChecked(checked: Boolean)
}
