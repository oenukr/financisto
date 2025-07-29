package ru.orangesoftware.orb

object Expressions {

    @JvmStatic
    fun eq(field: String, value: Any): Expression = Eq(field, value)

    @JvmStatic
    fun and(vararg ee: Expression): Expression = And(*ee)

    @JvmStatic
    fun or(e1: Expression, e2: Expression): Expression = Or(e1, e2)

    @JvmStatic
    fun or(vararg ee: Expression): Expression = Or(*ee)

    @JvmStatic
    fun lte(field: String, value: Any): Expression = Lte(field, value)

    @JvmStatic
    fun gte(field: String, value: Any): Expression = Gte(field, value)

    @JvmStatic
    fun lt(field: String, value: Any): Expression = Lt(field, value)

    @JvmStatic
    fun gt(field: String, value: Any): Expression = Gt(field, value)

    @JvmStatic
    fun btw(field: String, value1: Any, value2: Any): Expression = Btw(field, value1, value2)

    @JvmStatic
    fun like(field: String, value1: Any): Expression = Like(field, value1)
}
