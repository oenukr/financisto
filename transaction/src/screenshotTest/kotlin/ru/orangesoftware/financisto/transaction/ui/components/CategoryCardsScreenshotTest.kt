// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.ui.ComponentScreenshotPreviews
import ru.orangesoftware.financisto.transaction.ui.ScreenshotSurface

private val SampleCategories = listOf(
    CategoryOption(
        id = 1L,
        parentId = 0L,
        title = "Groceries",
        fullPath = "Food > Groceries",
        level = 1,
        isIncome = false,
    ),
    CategoryOption(
        id = 2L,
        parentId = 0L,
        title = "Restaurants",
        fullPath = "Food > Restaurants",
        level = 1,
        isIncome = false,
    ),
    CategoryOption(
        id = 3L,
        parentId = 0L,
        title = "Gasoline",
        fullPath = "Auto > Gasoline",
        level = 1,
        isIncome = false,
    ),
)

private val SampleProjects = listOf(ProjectOption(id = 1L, title = "Vacation"))

private val SampleSplits = listOf(
    EditableSplit(
        localId = 1L,
        categoryId = 1L,
        amountMinor = 3500L,
        isIncome = false,
        note = "Weekly groceries",
        projectId = 1L,
    ),
    EditableSplit(
        localId = 2L,
        categoryId = 2L,
        amountMinor = 1500L,
        isIncome = false,
        note = "Dinner with friends",
    ),
)

private val NoOpSplitCallbacks = SplitCategoryCallbacks(
    onExitSplitMode = {},
    onCreateCategoryClick = {},
    onAddSplitClick = {},
    onEditSplitClick = {},
    onRemoveSplitClick = {},
    onAdjustTotal = {},
    onDistributeEvenly = {},
    onAdjustLast = {},
    onCollapseSingleSplit = {},
)

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun SingleCategoryCardCollapsedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            SingleCategoryCard(
                selectedCategoryId = 1L,
                categories = SampleCategories,
                isIncomeMode = false,
                isExpanded = false,
                allowSplit = true,
                onToggleExpand = {},
                onSelectCategory = {},
                onClearCategory = {},
                onBrowseAllClick = {},
                onCreateCategoryClick = {},
                onEnableSplitClick = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun SingleCategoryCardExpandedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            SingleCategoryCard(
                selectedCategoryId = 1L,
                categories = SampleCategories,
                isIncomeMode = false,
                isExpanded = true,
                allowSplit = true,
                onToggleExpand = {},
                onSelectCategory = {},
                onClearCategory = {},
                onBrowseAllClick = {},
                onCreateCategoryClick = {},
                onEnableSplitClick = {},
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun SplitCategoryCardBalancedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            SplitCategoryCard(
                mode = TransactionMode.EXPENSE,
                totalMagnitudeMinor = 5000L,
                currencySymbol = "$",
                currencyDecimals = 2,
                splits = SampleSplits,
                categories = SampleCategories,
                projects = SampleProjects,
                callbacks = NoOpSplitCallbacks,
            )
        }
    }
}

@PreviewTest
@ComponentScreenshotPreviews
@Composable
fun SplitCategoryCardUnbalancedPreviewTest() {
    ScreenshotSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            SplitCategoryCard(
                mode = TransactionMode.EXPENSE,
                totalMagnitudeMinor = 6000L,
                currencySymbol = "$",
                currencyDecimals = 2,
                splits = SampleSplits,
                categories = SampleCategories,
                projects = SampleProjects,
                callbacks = NoOpSplitCallbacks,
            )
        }
    }
}
