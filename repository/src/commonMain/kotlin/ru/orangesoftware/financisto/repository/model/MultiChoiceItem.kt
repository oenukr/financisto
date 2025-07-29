package ru.orangesoftware.financisto.repository.model

interface MultiChoiceItem {
	val id: Long
	val title: String?
	var checked: Boolean
}
