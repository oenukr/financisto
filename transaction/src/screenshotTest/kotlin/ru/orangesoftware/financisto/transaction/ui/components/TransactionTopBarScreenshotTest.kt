// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

private const val SampleDateTimeMillis = 1715774400000L

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun TransactionTopBarCleared12hPreviewTest() {
    ScreenshotSurface {
        TransactionTopBar(
            dateTimeMillis = SampleDateTimeMillis,
            is24HourFormat = false,
            status = TransactionStatusOption.CLEARED,
            onBackClick = {},
            onDateClick = {},
            onTimeClick = {},
            onStatusClick = {},
            onClearFormClick = {},
            onOpenLegacyClick = {},
        )
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun TransactionTopBarUnreconciled24hPreviewTest() {
    ScreenshotSurface {
        TransactionTopBar(
            dateTimeMillis = SampleDateTimeMillis,
            is24HourFormat = true,
            status = TransactionStatusOption.UR,
            onBackClick = {},
            onDateClick = {},
            onTimeClick = {},
            onStatusClick = {},
            onClearFormClick = {},
            onOpenLegacyClick = {},
        )
    }
}
