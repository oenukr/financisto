package ru.orangesoftware.orb

class Or(vararg expressions: Expression) : CompoundExpression("OR", expressions.toList())
