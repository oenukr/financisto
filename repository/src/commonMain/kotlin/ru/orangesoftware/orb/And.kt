package ru.orangesoftware.orb

class And(vararg expressions: Expression) : CompoundExpression("AND", expressions.toList())
