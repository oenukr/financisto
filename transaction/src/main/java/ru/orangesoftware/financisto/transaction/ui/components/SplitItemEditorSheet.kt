// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitItemEditorSheet(
    split: EditableSplit,
    isNew: Boolean,
    categories: List<CategoryOption>,
    projects: List<ProjectOption>,
    currencyDecimals: Int,
    onUpdateDraft: (EditableSplit) -> Unit,
    onPickCategory: () -> Unit,
    onPickProject: () -> Unit,
    onOpenCalculator: () -> Unit,
    onSave: (EditableSplit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        SplitItemEditorSheetContent(
            split = split,
            isNew = isNew,
            categories = categories,
            projects = projects,
            currencyDecimals = currencyDecimals,
            onUpdateDraft = onUpdateDraft,
            onPickCategory = onPickCategory,
            onPickProject = onPickProject,
            onOpenCalculator = onOpenCalculator,
            onSave = onSave,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun SplitItemEditorSheetContent(
    split: EditableSplit,
    isNew: Boolean,
    categories: List<CategoryOption>,
    projects: List<ProjectOption>,
    currencyDecimals: Int,
    onUpdateDraft: (EditableSplit) -> Unit,
    onPickCategory: () -> Unit,
    onPickProject: () -> Unit,
    onOpenCalculator: () -> Unit,
    onSave: (EditableSplit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountState = rememberTextFieldState(
        initialText = AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals),
    )
    var isIncome by remember(split.localId, split.isIncome) { mutableStateOf(split.isIncome) }
    val noteState = rememberTextFieldState(initialText = split.note)
    SyncSplitEditorFieldsEffect(split, currencyDecimals, amountState, noteState)

    val selectedCategory = remember(categories, split.categoryId) {
        categories.firstOrNull { it.id == split.categoryId }
    }
    val selectedProject = remember(projects, split.projectId) {
        projects.firstOrNull { it.id == split.projectId && it.id > 0L }
    }
    val buildDraft = {
        val minor = AmountScale.parseMinorUnits(amountState.text.toString(), currencyDecimals) ?: 0L
        split.copy(amountMinor = minor, isIncome = isIncome, note = noteState.text.toString())
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SplitEditorHeaderAndMode(isNew = isNew, isIncome = isIncome, onIncomeChanged = { isIncome = it })
        SplitEditorFormFields(
            categoryName = selectedCategory?.fullPath ?: stringResource(R.string.tx_placeholder_uncategorized),
            projectName = selectedProject?.title ?: stringResource(R.string.tx_placeholder_no_project),
            amountState = amountState,
            noteState = noteState,
            onPickCategory = {
                onUpdateDraft(buildDraft())
                onPickCategory()
            },
            onOpenCalculator = {
                onUpdateDraft(buildDraft())
                onOpenCalculator()
            },
            onPickProject = {
                onUpdateDraft(buildDraft())
                onPickProject()
            },
        )
        SplitEditorActionButtons(onDismiss = onDismiss, onApply = { onSave(buildDraft()) })
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SyncSplitEditorFieldsEffect(
    split: EditableSplit,
    currencyDecimals: Int,
    amountState: TextFieldState,
    noteState: TextFieldState,
) {
    LaunchedEffect(split.localId, split.amountMinor) {
        val updatedAmount = AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals)
        if (amountState.text.toString() != updatedAmount) {
            amountState.setTextAndPlaceCursorAtEnd(updatedAmount)
        }
    }
    LaunchedEffect(split.localId, split.note) {
        if (noteState.text.toString() != split.note) {
            noteState.setTextAndPlaceCursorAtEnd(split.note)
        }
    }
}

@Composable
private fun SplitEditorHeaderAndMode(
    isNew: Boolean,
    isIncome: Boolean,
    onIncomeChanged: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(if (isNew) R.string.tx_sheet_add_split_item else R.string.tx_sheet_edit_split_item),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !isIncome,
            onClick = { onIncomeChanged(false) },
            label = { Text(stringResource(R.string.tx_chip_expense)) },
        )
        FilterChip(
            selected = isIncome,
            onClick = { onIncomeChanged(true) },
            label = { Text(stringResource(R.string.tx_chip_income)) },
        )
    }
}

@Composable
private fun SplitEditorFormFields(
    categoryName: String,
    projectName: String,
    amountState: TextFieldState,
    noteState: TextFieldState,
    onPickCategory: () -> Unit,
    onOpenCalculator: () -> Unit,
    onPickProject: () -> Unit,
) {
    SplitFieldPickerRow(
        label = stringResource(R.string.tx_label_category),
        value = categoryName,
        onClick = onPickCategory,
    )
    OutlinedTextField(
        state = amountState,
        inputTransformation = DecimalInputTransformation,
        label = { Text(stringResource(R.string.tx_label_amount)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            IconButton(onClick = onOpenCalculator) {
                Icon(
                    imageVector = Icons.Rounded.Calculate,
                    contentDescription = stringResource(R.string.tx_cd_calculator),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        state = noteState,
        label = { Text(stringResource(R.string.tx_label_note)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth(),
    )
    SplitFieldPickerRow(
        label = stringResource(R.string.tx_label_project),
        value = projectName,
        onClick = onPickProject,
    )
}

@Composable
private fun SplitFieldPickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile),
        color = FinancistoTheme.semanticColors.pillSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SplitEditorActionButtons(
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onDismiss, shape = CircleShape, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.tx_action_cancel))
        }
        Button(
            onClick = onApply,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.tx_action_apply))
        }
    }
}

private val SampleSplitEditorCategories = listOf(
    CategoryOption(
        id = 2L,
        parentId = 1L,
        title = "Groceries",
        fullPath = "Food : Groceries",
        level = 1,
        isIncome = false,
    ),
)

private val SampleEditorProjects = listOf(
    ProjectOption(id = 1L, title = "Home Renovation"),
    ProjectOption(id = 2L, title = "Vacation 2025"),
)

private val SampleEditableSplit = EditableSplit(
    localId = 1L,
    categoryId = 2L,
    amountMinor = 1250L,
    isIncome = false,
    note = "Weekly groceries",
    projectId = 1L,
)

@Preview(name = "Split Item Editor Sheet - Dark", showBackground = true)
@Composable
private fun SplitItemEditorSheetDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            SplitItemEditorSheetContent(
                split = SampleEditableSplit,
                isNew = false,
                categories = SampleSplitEditorCategories,
                projects = SampleEditorProjects,
                currencyDecimals = 2,
                onUpdateDraft = {},
                onPickCategory = {},
                onPickProject = {},
                onOpenCalculator = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
