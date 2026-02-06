package ru.orangesoftware.financisto.recur

import kotlin.time.Instant

interface RecurrenceProcessor {
    fun hasNext(): Boolean
    fun next(): Instant?
    fun fastForward(until: Instant)
}
