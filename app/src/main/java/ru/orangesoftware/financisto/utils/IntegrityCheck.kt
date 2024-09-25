package ru.orangesoftware.financisto.utils

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
    
    fun check(): Result
}
