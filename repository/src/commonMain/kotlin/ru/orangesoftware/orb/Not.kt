package ru.orangesoftware.orb

class Not(private val expression: Expression) : Expression {

    override fun toSelection(entityDefinition: ru.orangesoftware.orb.EntityDefinition): Selection {
        val selection = expression.toSelection(entityDefinition)
        return Selection("NOT (${selection.selection})", selection.selectionArgs)
    }
}
