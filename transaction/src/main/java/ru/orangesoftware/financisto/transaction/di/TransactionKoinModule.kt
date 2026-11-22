// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.TransactionPorts

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
    viewModel { (args: TransactionLaunchArgs) ->
        TransactionViewModel(
            launchArgs = args,
            savedStateHandle = get(),
            ports = get(),
            ioDispatcher = get(),
        )
    }
}
