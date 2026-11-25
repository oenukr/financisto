// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

@PreviewTest
@ScreenSizeMatrixPreviews
@ScreenMobileVariationPreviews
@Composable
fun TransactionScreenExpenseReadyPreviewTest() {
    TransactionScreen(
        uiState = TransactionScreenshotFixtures.expenseReadyState,
        resolveReceiptPath = { null },
        onIntent = {},
        onCloseRequested = {},
    )
}

@PreviewTest
@ScreenSizeMatrixPreviews
@ScreenMobileVariationPreviews
@Composable
fun TransactionScreenTransferDualCurrencyPreviewTest() {
    TransactionScreen(
        uiState = TransactionScreenshotFixtures.transferDualCurrencyReadyState,
        resolveReceiptPath = { null },
        onIntent = {},
        onCloseRequested = {},
    )
}

@PreviewTest
@ScreenSizeMatrixPreviews
@ScreenMobileVariationPreviews
@Composable
fun TransactionScreenSplitValidationErrorsPreviewTest() {
    TransactionScreen(
        uiState = TransactionScreenshotFixtures.splitUnbalancedWithErrorsReadyState,
        resolveReceiptPath = { null },
        onIntent = {},
        onCloseRequested = {},
    )
}

@PreviewTest
@ScreenSizeMatrixPreviews
@ScreenMobileVariationPreviews
@Composable
fun TransactionScreenLoadErrorPreviewTest() {
    TransactionScreen(
        uiState = TransactionUiState.LoadError("Database connection failed"),
        resolveReceiptPath = { null },
        onIntent = {},
        onCloseRequested = {},
    )
}

@PreviewTest
@ScreenMobileVariationPreviews
@Preview(name = "Mobile Light 400x500", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
fun TransactionScreenNoAccountsPreviewTest() {
    TransactionScreen(
        uiState = TransactionUiState.NoAccounts,
        resolveReceiptPath = { null },
        onIntent = {},
        onCloseRequested = {},
    )
}

@PreviewTest
@ScreenMobileVariationPreviews
@Preview(name = "Mobile Light 400x500", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
fun TransactionScreenNotFoundPreviewTest() {
    TransactionScreen(
        uiState = TransactionUiState.NotFound(transactionId = 99L),
        resolveReceiptPath = { null },
        onIntent = {},
        onCloseRequested = {},
    )
}
