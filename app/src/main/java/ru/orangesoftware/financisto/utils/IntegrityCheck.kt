package ru.orangesoftware.financisto.utils

import android.content.Context

interface IntegrityCheck {

    enum class Level {
        OK, INFO, WARN, ERROR
    }

    class Result(
        val level: Level,
        val message: String,
    ) {
        companion object {
            @JvmStatic
            val OK: Result = Result(Level.OK, "")
        }
    }

    fun check(context: Context): Result
}
