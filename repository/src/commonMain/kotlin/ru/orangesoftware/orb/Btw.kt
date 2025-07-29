package ru.orangesoftware.orb

class Btw(
    private val field: String,
    private val value1: Any,
    private val value2: Any,
) : Expression {
    override fun toSelection(ed: ru.orangesoftware.orb.EntityDefinition): Selection = Selection(
        "(${ed.getColumnForField(field)} between ? and ?)",
        listOf(value1.toString(), value2.toString()),
    )
}
