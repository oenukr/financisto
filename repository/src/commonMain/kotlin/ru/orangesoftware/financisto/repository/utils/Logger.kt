package ru.orangesoftware.financisto.repository.utils

interface Logger {
    fun e(message: String?, vararg args: Any?)
    fun e(t: Throwable?, message: String?, vararg args: Any?)
    fun d(message: String?, vararg args: Any?)
    fun i(message: String?, vararg args: Any?)
    fun i(t: Throwable?, message: String?, vararg args: Any?)
    fun w(message: String?, vararg args: Any?)
    fun w(t: Throwable?, message: String?, vararg args: Any?)
    // ... other logging levels as needed (w, i, v)
}
