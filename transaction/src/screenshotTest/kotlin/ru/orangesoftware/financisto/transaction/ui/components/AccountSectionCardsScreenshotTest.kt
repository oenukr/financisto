// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

private val SampleFromAccount = AccountOption(
    id = 1L,
    title = "Main Checking",
    typeLabel = "Bank",
    currencyId = 1L,
    currencyCode = "USD",
    currencySymbol = "$",
    currencyDecimals = 2,
    balanceMinor = 245075L,
)

private val SampleToAccount = AccountOption(
    id = 2L,
    title = "Travel Card",
    typeLabel = "Credit Card",
    currencyId = 2L,
    currencyCode = "EUR",
    currencySymbol = "€",
    currencyDecimals = 2,
    balanceMinor = 84050L,
)

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun SingleAccountAndCurrencyPillsPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            SingleAccountAndCurrencyPills(
                account = SampleFromAccount,
                selectedCurrency = CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
                showCurrencySelector = true,
                onAccountClick = {},
                onCurrencyClick = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun TransferAccountsGroupCardPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            TransferAccountsGroupCard(
                fromAccount = SampleFromAccount,
                toAccount = SampleToAccount,
                outAmountMinor = 15000L,
                inAmountMinor = 13850L,
                onFromAccountClick = {},
                onToAccountClick = {},
                onSwapClick = {},
            )
        }
    }
}
