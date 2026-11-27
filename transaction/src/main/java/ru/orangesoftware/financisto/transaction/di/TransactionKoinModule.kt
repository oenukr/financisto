// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.TransactionPorts
import kotlin.time.Clock

val transactionModule = module {
    single {
        TransactionPorts(
            repository = get(),
            preferencesPort = get(),
            draftSessionStore = get(),
            exchangeRatePort = get(),
            receiptMediaPort = get(),
        )
    }
    @Suppress("InjectDispatcher")
    viewModel { (args: TransactionLaunchArgs) ->
        TransactionViewModel(
            launchArgs = args,
            savedStateHandle = get(),
            ports = get(),
            ioDispatcher = getOrNull(),
            clock = getOrNull() ?: Clock.System,
            transformDispatcher = getOrNull(named("transformDispatcher")) ?: Dispatchers.Default,
        )
    }
}
