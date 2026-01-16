package ru.orangesoftware.financisto.utils

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import ru.orangesoftware.financisto.app.modules

class KoinInjectionTest {

        @OptIn(KoinExperimentalAPI::class)
        @Test
        fun checkKoinModule() {

            // Verify Koin configuration
            modules.forEach { it.verify(extraTypes = listOf(android.content.Context::class, ru.orangesoftware.financisto.http.HttpClientWrapper::class, ru.orangesoftware.financisto.utils.Logger::class, kotlin.time.Clock::class)) }
        }
}
