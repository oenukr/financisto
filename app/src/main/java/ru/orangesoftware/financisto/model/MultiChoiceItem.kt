package ru.orangesoftware.financisto.model

interface MultiChoiceItem {
	val id: Long
	val title: String?
	var checked: Boolean
}
