// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.domain.SplitAllocationCalculator
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode

@Composable
fun SplitCategoryCard(
    mode: TransactionMode,
    totalMagnitudeMinor: Long,
    currencySymbol: String,
    currencyDecimals: Int,
    splits: List<EditableSplit>,
    categories: List<CategoryOption>,
    projects: List<ProjectOption>,
    callbacks: SplitCategoryCallbacks,
    modifier: Modifier = Modifier,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val parentSigned = SplitAllocationCalculator.parentSignedMinor(totalMagnitudeMinor, mode)
    val unsplitSigned = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, splits)
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val projectById = remember(projects) { projects.filter { it.id > 0L }.associateBy { it.id } }

    FormFieldCardSurface(isActive = false, modifier = modifier) {
        SplitCardHeader(
            splitCount = splits.size,
            onExitSplitMode = callbacks.onExitSplitMode,
            onCreateCategoryClick = callbacks.onCreateCategoryClick,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = semantic.heroBorder)
        SplitAllocationStatusBar(
            unsplitSigned = unsplitSigned,
            currencySymbol = currencySymbol,
            currencyDecimals = currencyDecimals,
            canRebalance = splits.isNotEmpty(),
            canCollapseSingle = splits.size == 1,
            callbacks = callbacks,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            splits.forEachIndexed { index, split ->
                SplitItemRow(
                    index = index,
                    split = split,
                    category = categoryById[split.categoryId],
                    projectTitle = projectById[split.projectId]?.title,
                    currencySymbol = currencySymbol,
                    currencyDecimals = currencyDecimals,
                    onClick = { callbacks.onEditSplitClick(split.localId) },
                    onRemove = { callbacks.onRemoveSplitClick(split.localId) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AddSplitItemButton(onClick = callbacks.onAddSplitClick)
    }
}

@Composable
private fun AddSplitItemButton(onClick: () -> Unit) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(dimens.RadiusTile),
        border = BorderStroke(dimens.DefaultBorderWidth, MaterialTheme.colorScheme.primaryContainer),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = semantic.heroSurface,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.tx_action_add_split_item),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SplitCardHeader(
    splitCount: Int,
    onExitSplitMode: () -> Unit,
    onCreateCategoryClick: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconTile(
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                tintColor = semantic.activeBorder,
                bgColor = semantic.primaryTonal,
            )
            Column {
                Text(
                    text = stringResource(R.string.tx_label_category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(2.dp))
                SplitCountBadge(splitCount = splitCount)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ClearMiniIconButton(
                onClick = onExitSplitMode,
                contentDescription = stringResource(R.string.tx_cd_exit_split_mode),
            )
            AssistNewPill(
                label = stringResource(R.string.tx_action_new),
                icon = Icons.Rounded.Add,
                onClick = onCreateCategoryClick,
            )
        }
    }
}

@Composable
private fun SplitCountBadge(splitCount: Int) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    Row(
        modifier = Modifier
            .background(color = semantic.primaryTonal, shape = CircleShape)
            .border(width = dimens.DefaultBorderWidth, color = semantic.heroBorder, shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.CallSplit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.tx_split_categories_count, splitCount, splitCount),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SplitAllocationStatusBar(
    unsplitSigned: Long,
    currencySymbol: String,
    currencyDecimals: Int,
    canRebalance: Boolean,
    canCollapseSingle: Boolean,
    callbacks: SplitCategoryCallbacks,
) {
    val semantic = FinancistoTheme.semanticColors
    val isBalanced = unsplitSigned == 0L
    val unsplitFormatted = AmountScale.formatDisplayAmount(
        minorUnits = unsplitSigned,
        decimals = currencyDecimals,
        symbol = currencySymbol,
        includeSign = unsplitSigned != 0L,
    )
    val bannerBg = if (isBalanced) semantic.heroSurface else semantic.warningContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = bannerBg, shape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (isBalanced) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (isBalanced) semantic.onIncomeContainer else semantic.onWarningContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (isBalanced) {
                    stringResource(R.string.tx_split_fully_allocated, unsplitFormatted)
                } else {
                    stringResource(R.string.tx_split_unsplit_remaining, unsplitFormatted)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isBalanced) MaterialTheme.colorScheme.onSurface else semantic.onWarningContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canRebalance) {
            SplitRebalanceMenu(canCollapseSingle = canCollapseSingle, callbacks = callbacks)
        }
    }
}

@Composable
private fun SplitRebalanceMenu(
    canCollapseSingle: Boolean,
    callbacks: SplitCategoryCallbacks,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { menuExpanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Balance,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.tx_action_rebalance),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tx_rebalance_adjust_total)) },
                onClick = {
                    menuExpanded = false
                    callbacks.onAdjustTotal()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tx_rebalance_distribute_evenly)) },
                onClick = {
                    menuExpanded = false
                    callbacks.onDistributeEvenly()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tx_rebalance_adjust_last)) },
                onClick = {
                    menuExpanded = false
                    callbacks.onAdjustLast()
                },
            )
            if (canCollapseSingle) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tx_rebalance_collapse_single)) },
                    onClick = {
                        menuExpanded = false
                        callbacks.onCollapseSingleSplit()
                    },
                )
            }
        }
    }
}

@Composable
private fun SplitItemRow(
    index: Int,
    split: EditableSplit,
    category: CategoryOption?,
    projectTitle: String?,
    currencySymbol: String,
    currencyDecimals: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val semantic = FinancistoTheme.semanticColors
    val dimens = FinancistoTheme.dimens
    val isWarmTile = index % 2 == 0
    val tileBg = if (isWarmTile) semantic.splitContainer else semantic.primaryTonal
    val tileFg = if (isWarmTile) semantic.onSplitContainer else MaterialTheme.colorScheme.primary
    val signedAmount = SplitAllocationCalculator.splitSignedMinor(split)
    val amountBadgeBg = if (split.isIncome) semantic.incomeContainer else semantic.expenseContainer
    val amountBadgeFg = if (split.isIncome) semantic.onIncomeContainer else semantic.onExpenseContainer
    val projectPart = projectTitle?.let { stringResource(R.string.tx_split_project_prefix, it) }
    val subParts = listOfNotNull(
        category?.fullPath?.takeIf { it != category.title },
        split.note.takeIf { it.isNotBlank() },
        projectPart,
    ).joinToString(" • ")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dimens.RadiusTile),
        color = semantic.pillSurface,
        border = BorderStroke(dimens.DefaultBorderWidth, semantic.heroBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LeadingIconTile(icon = Icons.Rounded.Category, tintColor = tileFg, bgColor = tileBg)
                SplitItemLabels(split = split, category = category, subParts = subParts)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AmountScale.formatDisplayAmount(
                        minorUnits = signedAmount,
                        decimals = currencyDecimals,
                        symbol = currencySymbol,
                        includeSign = true,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountBadgeFg,
                    modifier = Modifier
                        .background(color = amountBadgeBg, shape = CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                ClearMiniIconButton(
                    onClick = onRemove,
                    contentDescription = stringResource(R.string.tx_cd_remove_split),
                )
            }
        }
    }
}

@Composable
private fun SplitItemLabels(
    split: EditableSplit,
    category: CategoryOption?,
    subParts: String,
) {
    Column {
        val rowTitle = category?.title ?: if (split.categoryId == TransactionIds.NO_CATEGORY_ID) {
            stringResource(R.string.tx_placeholder_uncategorized)
        } else {
            stringResource(R.string.tx_category_id_fallback, split.categoryId)
        }
        Text(
            text = rowTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subParts.isNotEmpty()) {
            Text(
                text = subParts,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SampleSplitCategories = listOf(
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
)

private val SampleSplitProjects = listOf(ProjectOption(id = 1L, title = "Vacation"))

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

@Preview(name = "Split Category Card - Balanced Light", showBackground = true)
@Composable
private fun SplitCategoryCardBalancedPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SplitCategoryCard(
                    mode = TransactionMode.EXPENSE,
                    totalMagnitudeMinor = 5000L,
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    splits = SampleSplits,
                    categories = SampleSplitCategories,
                    projects = SampleSplitProjects,
                    callbacks = NoOpSplitCallbacks,
                )
            }
        }
    }
}

@Preview(name = "Split Category Card - Unbalanced Dark", showBackground = true)
@Composable
private fun SplitCategoryCardUnbalancedDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                SplitCategoryCard(
                    mode = TransactionMode.EXPENSE,
                    totalMagnitudeMinor = 6000L,
                    currencySymbol = "$",
                    currencyDecimals = 2,
                    splits = SampleSplits,
                    categories = SampleSplitCategories,
                    projects = SampleSplitProjects,
                    callbacks = NoOpSplitCallbacks,
                )
            }
        }
    }
}
