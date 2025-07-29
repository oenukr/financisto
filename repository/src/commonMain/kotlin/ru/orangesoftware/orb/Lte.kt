package ru.orangesoftware.orb

data class Lte(val field: String, val value: Any) : Expression {
    override fun toSelection(ed: ru.orangesoftware.orb.EntityDefinition): Selection = Selection(
        "(${ed.getColumnForField(field)}<=?)",
        listOf(value.toString()),
    )
}
