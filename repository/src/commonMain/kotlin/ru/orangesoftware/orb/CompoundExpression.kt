package ru.orangesoftware.orb

open class CompoundExpression protected constructor(
    private val op: String,
    private val expressions: List<Expression>,
) : Expression {

    override fun toSelection(ed: ru.orangesoftware.orb.EntityDefinition): Selection {
        val selectionArgs = mutableListOf<String>()
        val selectionString =
            expressions.joinToString(separator = " $op ", prefix = "(", postfix = ")") { e ->
                val selection = e.toSelection(ed)
                selectionArgs.addAll(selection.selectionArgs)
                selection.selection
            }
        return Selection(selectionString, selectionArgs)
    }
}
