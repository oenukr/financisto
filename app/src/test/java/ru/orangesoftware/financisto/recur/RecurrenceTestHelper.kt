package ru.orangesoftware.financisto.recur

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import ru.orangesoftware.financisto.utils.Logger

object RecurrenceTestHelper {
    @JvmStatic
    fun start() {
        stopKoin()
        startKoin {
            modules(module {
                single<Logger> {
                    object : Logger {
                        override fun e(message: String?, vararg args: Any?) {}
                        override fun e(t: Throwable?, message: String?, vararg args: Any?) {}
                        override fun d(message: String?, vararg args: Any?) {}
                        override fun i(message: String?, vararg args: Any?) {}
                        override fun i(t: Throwable?, message: String?, vararg args: Any?) {}
                        override fun w(message: String?, vararg args: Any?) {}
                        override fun w(t: Throwable?, message: String?, vararg args: Any?) {}
                    }
                }
            })
        }
    }

    @JvmStatic
    fun stop() {
        stopKoin()
    }
}
