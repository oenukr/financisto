package ru.orangesoftware.orb

interface Expression {
    fun toSelection(ed: ru.orangesoftware.orb.EntityDefinition): Selection
}
