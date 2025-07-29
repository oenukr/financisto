package ru.orangesoftware.financisto.repository.model

interface SortableEntity {
    fun getSortOrder(): Long
}
