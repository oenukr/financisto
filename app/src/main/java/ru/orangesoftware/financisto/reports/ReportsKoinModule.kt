// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.reports

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.orangesoftware.financisto.app.TRANSFORM_DISPATCHER

val reportsAppStateModule = module {
    viewModel { (params: ReportLaunchParams) ->
        ReportViewModel(
            launchParams = params,
            db = get(),
            preferences = androidContext().getSharedPreferences(
                "ReportActivity_${params.reportTypeName}_DEFAULT",
                Context.MODE_PRIVATE,
            ),
            savedStateHandle = get(),
            ioDispatcher = get<CoroutineDispatcher>(),
            transformDispatcher = get<CoroutineDispatcher>(TRANSFORM_DISPATCHER),
        )
    }

    viewModel { (params: PieChartLaunchParams) ->
        PieChartViewModel(
            launchParams = params,
            db = get(),
            preferences = androidContext().getSharedPreferences(
                "ReportActivity_${params.reportTypeName}_DEFAULT",
                Context.MODE_PRIVATE,
            ),
            savedStateHandle = get(),
            ioDispatcher = get<CoroutineDispatcher>(),
            transformDispatcher = get<CoroutineDispatcher>(TRANSFORM_DISPATCHER),
        )
    }
}
