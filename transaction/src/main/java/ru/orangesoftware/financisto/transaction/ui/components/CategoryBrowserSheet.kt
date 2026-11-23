// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds

private const val CategoryBaseStartPaddingDp = 12
private const val CategoryIndentStepDp = 14

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowserSheet(
    categories: List<CategoryOption>,
    selectedCategoryId: Long,
    onSelect: (Long) -> Unit,
    onCreateCategory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        CategoryBrowserSheetContent(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onSelect = onSelect,
            onCreateCategory = onCreateCategory,
        )
    }
}

@Composable
fun CategoryBrowserSheetContent(
    categories: List<CategoryOption>,
    selectedCategoryId: Long,
    onSelect: (Long) -> Unit,
    onCreateCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queryState = rememberTextFieldState()
    val filtered by remember(categories) {
        derivedStateOf {
            val needle = queryState.text.toString().trim().lowercase()
            if (needle.isEmpty()) {
                categories
            } else {
                categories.filter {
                    it.title.lowercase().contains(needle) || it.fullPath.lowercase().contains(needle)
                }
            }
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        CategoryBrowserHeaderRow(onCreateCategory = onCreateCategory)
        OutlinedTextField(
            state = queryState,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text(stringResource(R.string.tx_placeholder_search_categories)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            item(key = TransactionIds.NO_CATEGORY_ID) {
                CategoryRowItem(
                    title = stringResource(R.string.tx_placeholder_uncategorized),
                    path = "",
                    indentLevel = 0,
                    selected = selectedCategoryId == TransactionIds.NO_CATEGORY_ID,
                    onClick = { onSelect(TransactionIds.NO_CATEGORY_ID) },
                )
            }
            items(filtered, key = { it.id }) { cat ->
                CategoryRowItem(
                    title = cat.title,
                    path = cat.fullPath,
                    indentLevel = if (queryState.text.isBlank()) cat.level else 0,
                    selected = cat.id == selectedCategoryId,
                    onClick = { onSelect(cat.id) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryBrowserHeaderRow(onCreateCategory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tx_sheet_select_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onCreateCategory) {
            Text(stringResource(R.string.tx_action_plus_new_category))
        }
    }
}

@Composable
private fun CategoryRowItem(
    title: String,
    path: String,
    indentLevel: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile),
        color = if (selected) semantic.topChipBackground else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                start = (CategoryBaseStartPaddingDp + indentLevel * CategoryIndentStepDp).dp,
                end = 12.dp,
                top = 9.dp,
                bottom = 9.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (path.isNotEmpty() && path != title) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val SampleBrowserCategories = listOf(
    CategoryOption(id = 1L, parentId = 0L, title = "Food", fullPath = "Food", level = 0, isIncome = false),
    CategoryOption(
        id = 2L,
        parentId = 1L,
        title = "Groceries",
        fullPath = "Food : Groceries",
        level = 1,
        isIncome = false,
    ),
    CategoryOption(id = 4L, parentId = 0L, title = "Transport", fullPath = "Transport", level = 0, isIncome = false),
)

@Preview(name = "Category Browser Sheet - Light", showBackground = true)
@Composable
private fun CategoryBrowserSheetPreview() {
    FinancistoTheme {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CategoryBrowserSheetContent(
                categories = SampleBrowserCategories,
                selectedCategoryId = 2L,
                onSelect = {},
                onCreateCategory = {},
            )
        }
    }
}
