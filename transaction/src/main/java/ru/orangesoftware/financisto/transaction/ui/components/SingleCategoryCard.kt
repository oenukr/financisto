// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.CategoryOption

private const val MaxCategorySuggestions = 4

@Composable
fun SingleCategoryCard(
    selectedCategoryId: Long,
    categories: List<CategoryOption>,
    isIncomeMode: Boolean,
    isExpanded: Boolean,
    allowSplit: Boolean,
    onToggleExpand: () -> Unit,
    onSelectCategory: (Long) -> Unit,
    onClearCategory: () -> Unit,
    onBrowseAllClick: () -> Unit,
    onCreateCategoryClick: () -> Unit,
    onEnableSplitClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selected = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }
    val suggestions = remember(categories, isIncomeMode) {
        categories.filter { it.isIncome == isIncomeMode }
            .ifEmpty { categories }
            .take(MaxCategorySuggestions)
    }

    FormFieldCardSurface(isActive = isExpanded, modifier = modifier) {
        SingleCategoryHeaderRow(
            selected = selected,
            isExpanded = isExpanded,
            allowSplit = allowSplit,
            enabled = enabled,
            onToggleExpand = onToggleExpand,
            onClearCategory = onClearCategory,
            onEnableSplitClick = onEnableSplitClick,
        )
        CapturedAnimatedVisibility(visible = isExpanded) {
            Column {
                SingleCategoryExpandedPanel(
                    suggestions = suggestions,
                    selectedCategoryId = selectedCategoryId,
                    allowSplit = allowSplit,
                    enabled = enabled,
                    onSelectCategory = onSelectCategory,
                    onBrowseAllClick = onBrowseAllClick,
                    onCreateCategoryClick = onCreateCategoryClick,
                    onEnableSplitClick = onEnableSplitClick,
                )
            }
        }
    }
}

@Composable
private fun SingleCategoryHeaderRow(
    selected: CategoryOption?,
    isExpanded: Boolean,
    allowSplit: Boolean,
    enabled: Boolean,
    onToggleExpand: () -> Unit,
    onClearCategory: () -> Unit,
    onEnableSplitClick: () -> Unit,
) {
    val breadcrumb = selected?.fullPath?.substringBeforeLast(" > ", "")?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FinancistoTheme.dimens.RadiusTile))
            .clickable(enabled = enabled, onClick = onToggleExpand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconTile(icon = Icons.Rounded.Category, isActive = isExpanded)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tx_label_category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (!breadcrumb.isNullOrEmpty()) {
                    Text(
                        text = breadcrumb.replace(" > ", " › "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = selected?.title ?: stringResource(R.string.tx_placeholder_uncategorized),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SingleCategoryHeaderActions(
            hasSelection = selected != null,
            allowSplit = allowSplit,
            enabled = enabled,
            onClearCategory = onClearCategory,
            onEnableSplitClick = onEnableSplitClick,
            onToggleExpand = onToggleExpand,
        )
    }
}

@Composable
private fun SingleCategoryHeaderActions(
    hasSelection: Boolean,
    allowSplit: Boolean,
    enabled: Boolean,
    onClearCategory: () -> Unit,
    onEnableSplitClick: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasSelection) {
            ClearMiniIconButton(
                onClick = onClearCategory,
                contentDescription = stringResource(R.string.tx_cd_clear_category),
                enabled = enabled,
            )
        }
        if (allowSplit) {
            IconButton(onClick = onEnableSplitClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                    contentDescription = stringResource(R.string.tx_cd_split_transaction),
                    tint = FinancistoTheme.semanticColors.activeBorder,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onToggleExpand, enabled = enabled) {
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = stringResource(R.string.tx_cd_category_suggestions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SingleCategoryExpandedPanel(
    suggestions: List<CategoryOption>,
    selectedCategoryId: Long,
    allowSplit: Boolean,
    enabled: Boolean,
    onSelectCategory: (Long) -> Unit,
    onBrowseAllClick: () -> Unit,
    onCreateCategoryClick: () -> Unit,
    onEnableSplitClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = semantic.heroBorder)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tx_header_category_suggestions),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline,
        )
        TextButton(
            onClick = onBrowseAllClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.tx_action_browse_all),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { cat ->
            CategorySuggestionItemRow(
                category = cat,
                isCurrent = cat.id == selectedCategoryId,
                enabled = enabled,
                onClick = { onSelectCategory(cat.id) },
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    SingleCategoryActionRow(
        allowSplit = allowSplit,
        enabled = enabled,
        onCreateCategoryClick = onCreateCategoryClick,
        onEnableSplitClick = onEnableSplitClick,
    )
}

@Composable
private fun CategorySuggestionItemRow(
    category: CategoryOption,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val itemShape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile)
    val bgColor = if (isCurrent) semantic.pillSurface else MaterialTheme.colorScheme.surface
    val parent = category.fullPath.substringBeforeLast(" > ", "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(itemShape)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (parent.isNotEmpty()) {
                Text(
                    text = parent.replace(" > ", " › "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.tx_badge_current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(color = semantic.primaryTonal, shape = CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SingleCategoryActionRow(
    allowSplit: Boolean,
    enabled: Boolean,
    onCreateCategoryClick: () -> Unit,
    onEnableSplitClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val buttonShape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile)
    val padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onCreateCategoryClick,
            enabled = enabled,
            shape = buttonShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = semantic.topChipBackground,
                contentColor = semantic.chipText,
            ),
            contentPadding = padding,
            modifier = Modifier.weight(1f),
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.tx_action_new_category),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (allowSplit) {
            FilledTonalButton(
                onClick = onEnableSplitClick,
                enabled = enabled,
                shape = buttonShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = semantic.primaryTonal,
                    contentColor = semantic.activeBorder,
                ),
                contentPadding = padding,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.tx_action_split_category),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val SampleSingleCategories = listOf(
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

@Preview(name = "Single Category Card - Collapsed Light", showBackground = true)
@Composable
private fun SingleCategoryCardCollapsedPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SingleCategoryCard(
                    selectedCategoryId = 1L,
                    categories = SampleSingleCategories,
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
}

@Preview(name = "Single Category Card - Expanded Dark", showBackground = true)
@Composable
private fun SingleCategoryCardExpandedDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SingleCategoryCard(
                    selectedCategoryId = 1L,
                    categories = SampleSingleCategories,
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
}
