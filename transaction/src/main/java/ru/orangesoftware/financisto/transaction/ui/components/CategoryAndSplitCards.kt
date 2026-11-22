// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.domain.SplitAllocationCalculator
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

private const val MAX_CATEGORY_SUGGESTIONS = 4

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
) {
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    val breadcrumb = selected?.fullPath?.substringBeforeLast(" > ", "")?.takeIf { it.isNotEmpty() }
    val suggestions = remember(categories, isIncomeMode) {
        categories.filter { it.isIncome == isIncomeMode }.ifEmpty { categories }.take(MAX_CATEGORY_SUGGESTIONS)
    }

    FormFieldCardSurface(isActive = isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
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
                        text = "Category",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = StitchColors.TextTertiary,
                    )
                    if (!breadcrumb.isNullOrEmpty()) {
                        Text(
                            text = breadcrumb.replace(" > ", " › "),
                            fontSize = 10.sp,
                            color = StitchColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = selected?.title ?: "Uncategorized",
                        fontSize = 14.sp,
                        fontWeight = if (selected != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected != null) StitchColors.TextPrimary else StitchColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selected != null) {
                    ClearMiniIconButton(onClick = onClearCategory, contentDescription = "Clear category")
                }
                if (allowSplit) {
                    IconButton(onClick = onEnableSplitClick, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                            contentDescription = "Split transaction",
                            tint = StitchColors.ActiveBorder,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Category suggestions",
                        tint = StitchColors.TextSecondary,
                    )
                }
            }
        }
        CapturedAnimatedVisibility(visible = isExpanded) {
            Column {
                SingleCategoryExpandedPanel(
                    suggestions = suggestions,
                    selectedCategoryId = selectedCategoryId,
                    allowSplit = allowSplit,
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
private fun SingleCategoryExpandedPanel(
    suggestions: List<CategoryOption>,
    selectedCategoryId: Long,
    allowSplit: Boolean,
    onSelectCategory: (Long) -> Unit,
    onBrowseAllClick: () -> Unit,
    onCreateCategoryClick: () -> Unit,
    onEnableSplitClick: () -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = StitchColors.HeroBorder)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CATEGORY SUGGESTIONS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = StitchColors.TextTertiary,
        )
        Text(
            text = "Browse All",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = StitchColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onBrowseAllClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { cat ->
            val isCurrent = cat.id == selectedCategoryId
            Surface(
                shape = RoundedCornerShape(StitchDimens.RadiusTile),
                color = if (isCurrent) StitchColors.PillSurface else StitchColors.SurfaceWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StitchDimens.RadiusTile))
                    .clickable { onSelectCategory(cat.id) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val parent = cat.fullPath.substringBeforeLast(" > ", "")
                        if (parent.isNotEmpty()) {
                            Text(parent.replace(" > ", " › "), fontSize = 10.sp, color = StitchColors.TextTertiary)
                        }
                        Text(
                            text = cat.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchColors.TextPrimary,
                        )
                    }
                    if (isCurrent) {
                        Surface(shape = CircleShape, color = StitchColors.PrimaryTonal) {
                            Text(
                                text = "Current",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StitchColors.OnPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(StitchDimens.RadiusTile),
            color = StitchColors.TopChipBg,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(StitchDimens.RadiusTile))
                .clickable(onClick = onCreateCategoryClick),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = StitchColors.ChipText,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = " New Category",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchColors.ChipText,
                )
            }
        }
        if (allowSplit) {
            Surface(
                shape = RoundedCornerShape(StitchDimens.RadiusTile),
                color = StitchColors.PrimaryTonal,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(StitchDimens.RadiusTile))
                    .clickable(onClick = onEnableSplitClick),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                        contentDescription = null,
                        tint = StitchColors.ActiveBorder,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = " Split Category",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchColors.ActiveBorder,
                    )
                }
            }
        }
    }
}

@Composable
fun SplitCategoryCard(
    mode: TransactionMode,
    totalMagnitudeMinor: Long,
    currencySymbol: String,
    currencyDecimals: Int,
    splits: List<EditableSplit>,
    categories: List<CategoryOption>,
    projects: List<ProjectOption>,
    onExitSplitMode: () -> Unit,
    onCreateCategoryClick: () -> Unit,
    onAddSplitClick: () -> Unit,
    onEditSplitClick: (Long) -> Unit,
    onRemoveSplitClick: (Long) -> Unit,
    onAdjustTotal: () -> Unit,
    onDistributeEvenly: () -> Unit,
    onAdjustLast: () -> Unit,
    onCollapseSingleSplit: () -> Unit,
) {
    val parentSigned = SplitAllocationCalculator.parentSignedMinor(totalMagnitudeMinor, mode)
    val unsplitSigned = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, splits)
    var rebalanceExpanded by remember { mutableStateOf(false) }

    FormFieldCardSurface(isActive = false) {
        SplitCardHeader(
            splitCount = splits.size,
            onExitSplitMode = onExitSplitMode,
            onCreateCategoryClick = onCreateCategoryClick,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = StitchColors.HeroBorder)
        SplitAllocationStatusBar(
            unsplitSigned = unsplitSigned,
            currencySymbol = currencySymbol,
            currencyDecimals = currencyDecimals,
            canRebalance = splits.isNotEmpty(),
            canCollapseSingle = splits.size == 1,
            menuExpanded = rebalanceExpanded,
            onMenuToggle = { rebalanceExpanded = it },
            onAdjustTotal = onAdjustTotal,
            onDistributeEvenly = onDistributeEvenly,
            onAdjustLast = onAdjustLast,
            onCollapseSingleSplit = onCollapseSingleSplit,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            splits.forEachIndexed { index, split ->
                val cat = categories.firstOrNull { it.id == split.categoryId }
                val proj = projects.firstOrNull { it.id == split.projectId && it.id > 0L }
                SplitItemRow(
                    index = index,
                    split = split,
                    category = cat,
                    projectTitle = proj?.title,
                    currencySymbol = currencySymbol,
                    currencyDecimals = currencyDecimals,
                    onClick = { onEditSplitClick(split.localId) },
                    onRemove = { onRemoveSplitClick(split.localId) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(StitchDimens.RadiusTile),
            color = StitchColors.HeroSurface,
            border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.PrimaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StitchDimens.RadiusTile))
                .clickable(onClick = onAddSplitClick),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = StitchColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = " Add split item",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun SplitCardHeader(
    splitCount: Int,
    onExitSplitMode: () -> Unit,
    onCreateCategoryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconTile(
                icon = Icons.AutoMirrored.Rounded.CallSplit,
                tintColor = StitchColors.ActiveBorder,
                bgColor = StitchColors.PrimaryTonal,
            )
            Column {
                Text("Category", fontSize = 11.sp, color = StitchColors.TextTertiary)
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = CircleShape,
                    color = StitchColors.PrimaryTonal,
                    border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                            contentDescription = null,
                            tint = StitchColors.Primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Split ($splitCount categories)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StitchColors.OnPrimaryContainer,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClearMiniIconButton(onClick = onExitSplitMode, contentDescription = "Exit split mode")
            AssistNewPill(label = "New", icon = Icons.Rounded.Add, onClick = onCreateCategoryClick)
        }
    }
}

@Composable
private fun SplitAllocationStatusBar(
    unsplitSigned: Long,
    currencySymbol: String,
    currencyDecimals: Int,
    canRebalance: Boolean,
    canCollapseSingle: Boolean,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onAdjustTotal: () -> Unit,
    onDistributeEvenly: () -> Unit,
    onAdjustLast: () -> Unit,
    onCollapseSingleSplit: () -> Unit,
) {
    val isBalanced = unsplitSigned == 0L
    val unsplitFormatted = AmountScale.formatDisplayAmount(
        minorUnits = unsplitSigned,
        decimals = currencyDecimals,
        symbol = currencySymbol,
        includeSign = unsplitSigned != 0L,
    )
    Surface(
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = if (isBalanced) StitchColors.HeroSurface else StitchColors.WarningBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                    tint = if (isBalanced) StitchColors.IncomeBadgeText else StitchColors.WarningText,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (isBalanced) {
                        "Fully allocated ($unsplitFormatted unsplit)"
                    } else {
                        "Unsplit: $unsplitFormatted"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isBalanced) StitchColors.TextPrimary else StitchColors.WarningText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canRebalance) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMenuToggle(true) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Balance,
                            contentDescription = null,
                            tint = StitchColors.Primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Rebalance",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchColors.Primary,
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuToggle(false) }) {
                        DropdownMenuItem(
                            text = { Text("Adjust total amount") },
                            onClick = { onMenuToggle(false); onAdjustTotal() },
                        )
                        DropdownMenuItem(
                            text = { Text("Distribute evenly") },
                            onClick = { onMenuToggle(false); onDistributeEvenly() },
                        )
                        DropdownMenuItem(
                            text = { Text("Adjust last split") },
                            onClick = { onMenuToggle(false); onAdjustLast() },
                        )
                        if (canCollapseSingle) {
                            DropdownMenuItem(
                                text = { Text("Collapse to single category") },
                                onClick = { onMenuToggle(false); onCollapseSingleSplit() },
                            )
                        }
                    }
                }
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
    val isWarmTile = index % 2 == 0
    val tileBg = if (isWarmTile) StitchColors.SplitWarmBg else StitchColors.PrimaryTonal
    val tileFg = if (isWarmTile) StitchColors.SplitWarmText else StitchColors.Primary
    val signedAmount = SplitAllocationCalculator.splitSignedMinor(split)
    val amountBadgeBg = if (split.isIncome) StitchColors.IncomeBadgeBg else StitchColors.ExpenseBadgeBg
    val amountBadgeFg = if (split.isIncome) StitchColors.IncomeBadgeText else StitchColors.ExpenseBadgeText
    val subParts = listOfNotNull(
        category?.fullPath?.takeIf { it != category.title },
        split.note.takeIf { it.isNotBlank() },
        projectTitle?.let { "Project: $it" },
    ).joinToString(" • ")

    Surface(
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = StitchColors.PillSurface,
        border = BorderStroke(StitchDimens.DefaultBorderWidth, StitchColors.HeroBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StitchDimens.RadiusTile))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LeadingIconTile(
                    icon = Icons.Rounded.Category,
                    tintColor = tileFg,
                    bgColor = tileBg,
                )
                Column(modifier = Modifier.weight(1f)) {
                    val rowTitle = category?.title ?: if (split.categoryId == TransactionIds.NO_CATEGORY_ID) {
                        "Uncategorized"
                    } else {
                        "#${split.categoryId}"
                    }
                    Text(
                        text = rowTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StitchColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subParts.isNotEmpty()) {
                        Text(
                            text = subParts,
                            fontSize = 10.sp,
                            color = StitchColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = CircleShape, color = amountBadgeBg) {
                    Text(
                        text = AmountScale.formatDisplayAmount(
                            minorUnits = signedAmount,
                            decimals = currencyDecimals,
                            symbol = currencySymbol,
                            includeSign = true,
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountBadgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                ClearMiniIconButton(onClick = onRemove, contentDescription = "Remove split")
            }
        }
    }
}

private val sampleCategories = listOf(
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

@Preview(name = "Single Category Card - Collapsed", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SingleCategoryCardCollapsedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SingleCategoryCard(
                selectedCategoryId = 1L,
                categories = sampleCategories,
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

@Preview(name = "Single Category Card - Expanded", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SingleCategoryCardExpandedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SingleCategoryCard(
                selectedCategoryId = 1L,
                categories = sampleCategories,
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

private val sampleProjects = listOf(
    ProjectOption(id = 1L, title = "Vacation"),
)

private val sampleSplits = listOf(
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

@Preview(name = "Split Category Card - Balanced", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SplitCategoryCardBalancedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SplitCategoryCard(
                mode = TransactionMode.EXPENSE,
                totalMagnitudeMinor = 5000L,
                currencySymbol = "$",
                currencyDecimals = 2,
                splits = sampleSplits,
                categories = sampleCategories,
                projects = sampleProjects,
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
        }
    }
}

@Preview(name = "Split Category Card - Unbalanced", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SplitCategoryCardUnbalancedPreview() {
    TransactionStitchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SplitCategoryCard(
                mode = TransactionMode.EXPENSE,
                totalMagnitudeMinor = 6000L,
                currencySymbol = "$",
                currencyDecimals = 2,
                splits = sampleSplits,
                categories = sampleCategories,
                projects = sampleProjects,
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
        }
    }
}
