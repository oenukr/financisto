// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ExchangeRateCardEditablePreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            ExchangeRateCard(
                rateInput = "0.92",
                rateValue = 0.92,
                fromCode = "USD",
                toCode = "EUR",
                isFetchingRate = false,
                onRateInputChange = {},
                onSyncRateClick = {},
                onOpenRateCalculator = {},
            )
        }
    }
}
