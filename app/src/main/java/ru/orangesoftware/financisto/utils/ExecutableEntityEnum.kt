package ru.orangesoftware.financisto.utils

interface ExecutableEntityEnum<V> : EntityEnum {
    fun execute(value: V)
}
