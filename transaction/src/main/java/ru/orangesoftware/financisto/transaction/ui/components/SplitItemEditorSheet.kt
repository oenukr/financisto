// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption

private class SplitSyncBaseline(
    var amountMinor: Long,
    var note: String,
    var isIncome: Boolean,
    var categoryId: Long,
    var currencyDecimals: Int,
) {
    fun toDraft(
        splitLocalId: Long,
        amountText: String,
        noteText: String,
        currentIsIncome: Boolean,
    ): SplitEditorDraft = SplitEditorDraft(
        splitLocalId = splitLocalId,
        amountText = amountText,
        noteText = noteText,
        isIncome = currentIsIncome,
        syncedAmountMinor = amountMinor,
        syncedNote = note,
        syncedIsIncome = isIncome,
        syncedCategoryId = categoryId,
        syncedCurrencyDecimals = currencyDecimals,
    )

    companion object {
        fun fromDraft(draft: SplitEditorDraft): SplitSyncBaseline = SplitSyncBaseline(
            amountMinor = draft.syncedAmountMinor,
            note = draft.syncedNote,
            isIncome = draft.syncedIsIncome,
            categoryId = draft.syncedCategoryId,
            currencyDecimals = draft.syncedCurrencyDecimals,
        )
    }
}

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
    enabled: Boolean = true,
    cachedDraft: SplitEditorDraft? = null,
    onDraftChanged: (SplitEditorDraft) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = { if (enabled) onDismiss() },
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
            enabled = enabled,
            cachedDraft = cachedDraft,
            onDraftChanged = onDraftChanged,
        )
    }
}

private class SplitEditorHolder(
    val amountState: TextFieldState,
    val noteState: TextFieldState,
    val isIncomeState: MutableState<Boolean>,
    val showAmountErrorState: MutableState<Boolean>,
    private val baseline: SplitSyncBaseline,
    private val onDraftChanged: (SplitEditorDraft) -> Unit,
) {
    fun publishDraft(splitLocalId: Long) {
        onDraftChanged(
            baseline.toDraft(
                splitLocalId = splitLocalId,
                amountText = amountState.text.toString(),
                noteText = noteState.text.toString(),
                currentIsIncome = isIncomeState.value,
            ),
        )
    }

    fun updateIncome(splitLocalId: Long, nextIncome: Boolean) {
        isIncomeState.value = nextIncome
        publishDraft(splitLocalId)
    }

    fun commitAndRun(
        split: EditableSplit,
        currencyDecimals: Int,
        onUpdateDraft: (EditableSplit) -> Unit,
        action: () -> Unit,
    ) {
        val noteText = noteState.text.toString()
        val minor = parseValidSplitAmountMinor(amountState.text.toString(), currencyDecimals) ?: split.amountMinor
        baseline.amountMinor = minor
        baseline.note = noteText
        baseline.isIncome = isIncomeState.value
        publishDraft(split.localId)
        onUpdateDraft(split.copy(amountMinor = minor, isIncome = isIncomeState.value, note = noteText))
        action()
    }

    fun applySave(
        split: EditableSplit,
        currencyDecimals: Int,
        onSave: (EditableSplit) -> Unit,
    ) {
        val parsedMinor = parseValidSplitAmountMinor(amountState.text.toString(), currencyDecimals)
        if (parsedMinor == null) {
            showAmountErrorState.value = true
        } else {
            showAmountErrorState.value = false
            onSave(
                split.copy(
                    amountMinor = parsedMinor,
                    isIncome = isIncomeState.value,
                    note = noteState.text.toString(),
                ),
            )
        }
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
    enabled: Boolean = true,
    cachedDraft: SplitEditorDraft? = null,
    onDraftChanged: (SplitEditorDraft) -> Unit = {},
) {
    key(split.localId) {
        val holder = rememberSplitEditorHolder(
            split = split,
            currencyDecimals = currencyDecimals,
            cachedDraft = cachedDraft,
            onDraftChanged = onDraftChanged,
        )
        val selectedCategory = remember(categories, split.categoryId) {
            categories.firstOrNull { it.id == split.categoryId }
        }
        val selectedProject = remember(projects, split.projectId) {
            projects.firstOrNull { it.id == split.projectId && it.id > 0L }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SplitEditorHeaderAndMode(
                    isNew = isNew,
                    isIncome = holder.isIncomeState.value,
                    enabled = enabled,
                    onIncomeChanged = { holder.updateIncome(split.localId, it) },
                )
                SplitEditorFormFields(
                    categoryName = selectedCategory?.fullPath
                        ?: stringResource(R.string.tx_placeholder_uncategorized),
                    projectName = selectedProject?.title ?: stringResource(R.string.tx_placeholder_no_project),
                    amountState = holder.amountState,
                    showAmountError = holder.showAmountErrorState.value,
                    noteState = holder.noteState,
                    enabled = enabled,
                    onPickCategory = { holder.commitAndRun(split, currencyDecimals, onUpdateDraft, onPickCategory) },
                    onOpenCalculator = {
                        holder.commitAndRun(split, currencyDecimals, onUpdateDraft, onOpenCalculator)
                    },
                    onPickProject = { holder.commitAndRun(split, currencyDecimals, onUpdateDraft, onPickProject) },
                )
            }
            SplitEditorActionButtons(
                enabled = enabled,
                onDismiss = onDismiss,
                onApply = { holder.applySave(split, currencyDecimals, onSave) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun rememberSplitEditorHolder(
    split: EditableSplit,
    currencyDecimals: Int,
    cachedDraft: SplitEditorDraft?,
    onDraftChanged: (SplitEditorDraft) -> Unit,
): SplitEditorHolder {
    val initialDraft = remember(split.localId) {
        SplitEditorDraft.resolve(split, currencyDecimals, cachedDraft)
    }
    val baseline = remember(split.localId) { SplitSyncBaseline.fromDraft(initialDraft) }
    val amountState = rememberTextFieldState(initialText = initialDraft.amountText)
    val noteState = rememberTextFieldState(initialText = initialDraft.noteText)
    val isIncomeState = remember(split.localId) { mutableStateOf(initialDraft.isIncome) }
    val showAmountErrorState = remember(split.localId, split.amountMinor) { mutableStateOf(false) }
    val latestOnDraftChanged by rememberUpdatedState(onDraftChanged)
    val holder = remember(split.localId, amountState, noteState, isIncomeState, showAmountErrorState) {
        SplitEditorHolder(
            amountState = amountState,
            noteState = noteState,
            isIncomeState = isIncomeState,
            showAmountErrorState = showAmountErrorState,
            baseline = baseline,
            onDraftChanged = { latestOnDraftChanged(it) },
        )
    }
    SyncSplitEditorFieldsEffect(
        split = split,
        currencyDecimals = currencyDecimals,
        baseline = baseline,
        amountState = amountState,
        noteState = noteState,
        forceSyncAmount = cachedDraft?.splitLocalId == split.localId && cachedDraft.forceSyncAmount,
        forceSyncIsIncome = cachedDraft?.splitLocalId == split.localId && cachedDraft.forceSyncIsIncome,
        onSyncIncome = { isIncomeState.value = it },
        onAmountTextEdited = { showAmountErrorState.value = false },
        onPublishDraft = { holder.publishDraft(split.localId) },
    )
    return holder
}

@Composable
private fun SyncSplitEditorFieldsEffect(
    split: EditableSplit,
    currencyDecimals: Int,
    baseline: SplitSyncBaseline,
    amountState: TextFieldState,
    noteState: TextFieldState,
    forceSyncAmount: Boolean,
    forceSyncIsIncome: Boolean,
    onSyncIncome: (Boolean) -> Unit,
    onAmountTextEdited: () -> Unit,
    onPublishDraft: () -> Unit,
) {
    val latestOnSyncIncome by rememberUpdatedState(onSyncIncome)
    val latestOnAmountTextEdited by rememberUpdatedState(onAmountTextEdited)
    val latestOnPublishDraft by rememberUpdatedState(onPublishDraft)

    LaunchedEffect(split.localId, split.amountMinor, currencyDecimals, forceSyncAmount) {
        val changed = forceSyncAmount ||
            split.amountMinor != baseline.amountMinor ||
            currencyDecimals != baseline.currencyDecimals
        if (changed) {
            val updatedAmount = AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals)
            if (amountState.text.toString() != updatedAmount) {
                amountState.setTextAndPlaceCursorAtEnd(updatedAmount)
            }
            baseline.amountMinor = split.amountMinor
            baseline.currencyDecimals = currencyDecimals
            latestOnPublishDraft()
        }
    }
    LaunchedEffect(split.localId, split.note) {
        if (split.note != baseline.note) {
            if (noteState.text.toString() != split.note) {
                noteState.setTextAndPlaceCursorAtEnd(split.note)
            }
            baseline.note = split.note
            latestOnPublishDraft()
        }
    }
    LaunchedEffect(split.localId, split.isIncome, split.categoryId, forceSyncIsIncome) {
        val changed = forceSyncIsIncome ||
            split.isIncome != baseline.isIncome ||
            split.categoryId != baseline.categoryId
        if (changed) {
            latestOnSyncIncome(split.isIncome)
            baseline.isIncome = split.isIncome
            baseline.categoryId = split.categoryId
            latestOnPublishDraft()
        }
    }
    LaunchedEffect(amountState, noteState) {
        snapshotFlow { amountState.text.toString() to noteState.text.toString() }
            .distinctUntilChanged()
            .collect { latestOnPublishDraft() }
    }
    LaunchedEffect(amountState) {
        snapshotFlow { amountState.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .collect { latestOnAmountTextEdited() }
    }
    DisposableEffect(split.localId) {
        onDispose { latestOnPublishDraft() }
    }
}

private fun parseValidSplitAmountMinor(rawInput: String, decimals: Int): Long? {
    val trimmed = rawInput.trim()
    val validText = trimmed.isNotEmpty() && isValidDecimalInput(trimmed)
    return if (validText) {
        AmountScale.parseMinorUnits(trimmed, decimals)?.takeIf { it > 0L }
    } else {
        null
    }
}

@Composable
private fun SplitEditorHeaderAndMode(
    isNew: Boolean,
    isIncome: Boolean,
    enabled: Boolean,
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
            enabled = enabled,
            label = { Text(stringResource(R.string.tx_chip_expense)) },
        )
        FilterChip(
            selected = isIncome,
            onClick = { onIncomeChanged(true) },
            enabled = enabled,
            label = { Text(stringResource(R.string.tx_chip_income)) },
        )
    }
}

@Composable
private fun SplitEditorFormFields(
    categoryName: String,
    projectName: String,
    amountState: TextFieldState,
    showAmountError: Boolean,
    noteState: TextFieldState,
    enabled: Boolean,
    onPickCategory: () -> Unit,
    onOpenCalculator: () -> Unit,
    onPickProject: () -> Unit,
) {
    SplitFieldPickerRow(
        label = stringResource(R.string.tx_label_category),
        value = categoryName,
        enabled = enabled,
        onClick = onPickCategory,
    )
    OutlinedTextField(
        state = amountState,
        enabled = enabled,
        inputTransformation = DecimalInputTransformation,
        label = { Text(stringResource(R.string.tx_label_amount)) },
        isError = showAmountError,
        supportingText = if (showAmountError) {
            { Text(stringResource(R.string.tx_error_invalid_split_amount)) }
        } else {
            null
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            IconButton(onClick = onOpenCalculator, enabled = enabled) {
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
        enabled = enabled,
        label = { Text(stringResource(R.string.tx_label_note)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth(),
    )
    SplitFieldPickerRow(
        label = stringResource(R.string.tx_label_project),
        value = projectName,
        enabled = enabled,
        onClick = onPickProject,
    )
}

@Composable
private fun SplitFieldPickerRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
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
    enabled: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onDismiss,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.tx_action_cancel))
        }
        Button(
            onClick = onApply,
            enabled = enabled,
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
