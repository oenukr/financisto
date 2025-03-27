package ru.orangesoftware.financisto.utils

import timber.log.Timber

class TimberLogger : Logger {
    override fun e(message: String?, vararg args: Any?) {
        Timber.e(message, *args)
    }

    override fun e(t: Throwable?, message: String?, vararg args: Any?) {
        Timber.e(t, message, *args)
    }

    override fun d(message: String?, vararg args: Any?) {
        Timber.d(message, *args)
    }

    override fun i(message: String?, vararg args: Any?) {
        Timber.i(message, *args)
    }

    override fun i(t: Throwable?, message: String?, vararg args: Any?) {
        Timber.i(t, message, *args)
    }

    override fun w(message: String?, vararg args: Any?) {
        Timber.w(message, *args)
    }

    override fun w(t: Throwable?, message: String?, vararg args: Any?) {
        Timber.w(t, message, *args)
    }

    // ... implement other logging levels with Timber as needed
}
