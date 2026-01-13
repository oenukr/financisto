package ru.orangesoftware.financisto.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

object TestKoinHelper {
    fun start(client: HttpClientWrapper) {
        stopKoin()
        startKoin {
            modules(module {
                single { client }
                single<ru.orangesoftware.financisto.utils.Logger> {
                    object : ru.orangesoftware.financisto.utils.Logger {
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

    fun stop() {
        stopKoin()
    }

    fun createDummyClient(): HttpClient {
        return HttpClient(CIO)
    }
}
