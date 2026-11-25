// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun HeroAmountCardSingleCurrencyExpensePreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            HeroAmountCard(
                model = HeroAmountDisplayModel(
                    mode = TransactionMode.EXPENSE,
                    isDualCurrency = false,
                    primaryInput = "125.00",
                    primaryCurrencyCode = "USD",
                    primaryCurrencySymbol = "$",
                    primaryAccountLabel = "Main Checking",
                    secondaryInput = "",
                    secondaryCurrencyCode = "",
                    secondaryCurrencySymbol = "",
                    secondaryAccountLabel = "",
                ),
                onSwitchMode = {},
                onCycleSignOrMode = {},
                onPrimaryAmountChanged = {},
                onSecondaryAmountChanged = {},
                onOpenPrimaryCalculator = {},
                onOpenSecondaryCalculator = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun HeroAmountCardDualCurrencyTransferPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            HeroAmountCard(
                model = HeroAmountDisplayModel(
                    mode = TransactionMode.TRANSFER,
                    isDualCurrency = true,
                    primaryInput = "100.00",
                    primaryCurrencyCode = "USD",
                    primaryCurrencySymbol = "$",
                    primaryAccountLabel = "From Main Checking",
                    secondaryInput = "92.50",
                    secondaryCurrencyCode = "EUR",
                    secondaryCurrencySymbol = "€",
                    secondaryAccountLabel = "To Travel Card",
                ),
                onSwitchMode = {},
                onCycleSignOrMode = {},
                onPrimaryAmountChanged = {},
                onSecondaryAmountChanged = {},
                onOpenPrimaryCalculator = {},
                onOpenSecondaryCalculator = {},
            )
        }
    }
}
