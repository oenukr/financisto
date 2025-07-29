package ru.orangesoftware.orb

class Eq(private val field: String, private val value: Any) : Expression {

    override fun toSelection(ed: ru.orangesoftware.orb.EntityDefinition): Selection = Selection(
        "(${ed.getColumnForField(field)}=?)",
        listOf(value.toString())
    )
}
