// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface
import ru.orangesoftware.financisto.transaction.ui.ValidationErrorsBanner

private val SampleHierarchicalCategories = listOf(
    CategoryOption(
        id = 1L,
        parentId = 0L,
        title = "Food",
        fullPath = "Food",
        level = 0,
        isIncome = false,
    ),
    CategoryOption(
        id = 2L,
        parentId = 1L,
        title = "Groceries",
        fullPath = "Food : Groceries",
        level = 1,
        isIncome = false,
    ),
    CategoryOption(
        id = 3L,
        parentId = 2L,
        title = "Organic Market",
        fullPath = "Food : Groceries : Organic Market",
        level = 2,
        isIncome = false,
    ),
    CategoryOption(
        id = 4L,
        parentId = 0L,
        title = "Transport",
        fullPath = "Transport",
        level = 0,
        isIncome = false,
    ),
)

private val SampleProjects = listOf(
    ProjectOption(id = 1L, title = "Home Renovation"),
    ProjectOption(id = 2L, title = "Europe Trip"),
    ProjectOption(id = 3L, title = "Old Relocation", isActive = false),
)

private val SampleSplit = EditableSplit(
    localId = 1L,
    categoryId = 2L,
    amountMinor = 1250L,
    isIncome = false,
    note = "Weekly groceries",
    projectId = 1L,
)

private val SampleSplitDraft = SplitEditorDraft(
    splitLocalId = 1L,
    amountText = "12.50",
    noteText = "Weekly groceries",
    isIncome = false,
    syncedAmountMinor = 1250L,
    syncedNote = "Weekly groceries",
    syncedIsIncome = false,
    syncedCategoryId = 2L,
    syncedCurrencyDecimals = 2,
)

private val SampleAccounts = listOf(
    AccountOption(
        id = 1L,
        title = "Main Checking",
        typeLabel = "Bank",
        currencyId = 1L,
        currencyCode = "USD",
        currencySymbol = "$",
        currencyDecimals = 2,
        balanceMinor = 245075L,
    ),
    AccountOption(
        id = 2L,
        title = "Travel Card",
        typeLabel = "Credit Card",
        currencyId = 2L,
        currencyCode = "EUR",
        currencySymbol = "€",
        currencyDecimals = 2,
        balanceMinor = -42000L,
    ),
    AccountOption(
        id = 3L,
        title = "Legacy Savings",
        typeLabel = "Cash",
        currencyId = 1L,
        currencyCode = "USD",
        currencySymbol = "$",
        currencyDecimals = 2,
        balanceMinor = 0L,
        isActive = false,
    ),
)

private val SampleCurrencies = listOf(
    CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
    CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
    CurrencyOption(id = 3L, code = "JPY", symbol = "¥", decimals = 0),
)

private val SampleValidationErrors = listOf(
    "Amount must be greater than zero",
    "Split amounts do not match total amount",
    "Destination account must differ from source account",
)

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun CalculatorBottomSheetExpressionPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CalculatorBottomSheetContent(
                target = CalculatorTarget.PRIMARY_AMOUNT,
                expressionState = rememberTextFieldState(initialText = "125+40"),
                errorMsg = null,
                onError = {},
                onClearError = {},
                onApplyValue = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun CalculatorBottomSheetErrorPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CalculatorBottomSheetContent(
                target = CalculatorTarget.PRIMARY_AMOUNT,
                expressionState = rememberTextFieldState(initialText = "125/0"),
                errorMsg = "Division by zero",
                onError = {},
                onClearError = {},
                onApplyValue = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun CategoryBrowserSheetPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CategoryBrowserSheetContent(
                categories = SampleHierarchicalCategories,
                selectedCategoryId = 3L,
                onSelect = {},
                onCreateCategory = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ProjectPickerSheetPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            ProjectPickerSheetContent(
                projects = SampleProjects,
                selectedProjectId = 1L,
                pendingProjectName = null,
                allowCreateInline = true,
                onSelectProject = {},
                onCreatePendingProject = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun SplitItemEditorSheetPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            SplitItemEditorSheetContent(
                split = SampleSplit,
                isNew = false,
                categories = SampleHierarchicalCategories,
                projects = SampleProjects,
                currencyDecimals = 2,
                onUpdateDraft = {},
                onPickCategory = {},
                onPickProject = {},
                onOpenCalculator = {},
                onSave = {},
                onDismiss = {},
                cachedDraft = SampleSplitDraft,
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun AccountPickerSheetPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            AccountPickerSheetContent(
                title = "Select Account",
                accounts = SampleAccounts,
                selectedAccountId = 1L,
                onSelect = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun CurrencyPickerSheetPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CurrencyPickerSheetContent(
                currencies = SampleCurrencies,
                accountCurrencyCode = "USD",
                selectedOriginalCurrencyId = 2L,
                onSelect = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun StatusPickerSheetPreviewTest() {
    ScreenshotSurface {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            StatusPickerSheetContent(
                selectedStatus = TransactionStatusOption.CLEARED,
                onSelect = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ValidationErrorsBannerPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            ValidationErrorsBanner(errors = SampleValidationErrors)
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun DiscardTransactionDialogPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.size(400.dp, 320.dp)) {
            DiscardTransactionDialog(
                isAiDraft = false,
                onConfirmDiscard = {},
                onKeepEditing = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun ConsumedDraftAlertDialogPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.size(400.dp, 320.dp)) {
            ConsumedDraftAlertDialog(
                savedTransactionId = 42L,
                onOpenSaved = {},
                onCreateDuplicate = {},
                onCancel = {},
            )
        }
    }
}
