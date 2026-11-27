// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.utils

import androidx.lifecycle.SavedStateHandle
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import ru.orangesoftware.financisto.activity.AboutViewModel
import ru.orangesoftware.financisto.app.modules
import ru.orangesoftware.financisto.reports.PieChartLaunchParams
import ru.orangesoftware.financisto.reports.PieChartViewModel
import ru.orangesoftware.financisto.reports.ReportLaunchParams
import ru.orangesoftware.financisto.reports.ReportViewModel
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.ui.PermissionsViewModel

class KoinInjectionTest {

        @OptIn(KoinExperimentalAPI::class)
        @Test
        fun checkKoinModule() {

            // Verify Koin configuration
            org.koin.dsl.module { includes(modules) }.verify(
                extraTypes = listOf(android.content.Context::class),
                injections = injectedParameters(
                    definition<TransactionViewModel>(TransactionLaunchArgs::class, SavedStateHandle::class),
                    definition<AboutViewModel>(String::class, SavedStateHandle::class),
                    definition<PermissionsViewModel>(Function1::class),
                    definition<ReportViewModel>(ReportLaunchParams::class, SavedStateHandle::class),
                    definition<PieChartViewModel>(PieChartLaunchParams::class, SavedStateHandle::class),
                ),
            )
        }
}
