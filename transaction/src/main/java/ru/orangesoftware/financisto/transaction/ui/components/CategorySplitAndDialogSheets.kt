// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBrowserSheet(
    categories: List<CategoryOption>,
    selectedCategoryId: Long,
    onSelect: (Long) -> Unit,
    onCreateCategory: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
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
) {
    val queryState = rememberTextFieldState()
    val filtered by remember(categories) {
        computedStateOf {
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
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Select Category", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCreateCategory) { Text("+ New Category") }
        }
        OutlinedTextField(
            state = queryState,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text("Search categories...") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            item {
                CategoryRowItem(
                    title = "Uncategorized",
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
private fun CategoryRowItem(
    title: String,
    path: String,
    indentLevel: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = if (selected) StitchColors.TopChipBg else StitchColors.SurfaceWhite,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                start = (12 + indentLevel * 14).dp,
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
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = StitchColors.TextPrimary,
                )
                if (path.isNotEmpty() && path != title) {
                    Text(text = path, fontSize = 10.sp, color = StitchColors.TextTertiary)
                }
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = StitchColors.Primary)
            }
        }
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
    onPickCategory: () -> Unit,
    onPickProject: () -> Unit,
    onOpenCalculator: () -> Unit,
    onSave: (EditableSplit) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
        SplitItemEditorSheetContent(
            split = split,
            isNew = isNew,
            categories = categories,
            projects = projects,
            currencyDecimals = currencyDecimals,
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
    onPickCategory: () -> Unit,
    onPickProject: () -> Unit,
    onOpenCalculator: () -> Unit,
    onSave: (EditableSplit) -> Unit,
    onDismiss: () -> Unit,
) {
    val amountState = rememberTextFieldState(
        initialText = AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals)
    )
    var isIncome by remember(split.localId, split.isIncome) { mutableStateOf(split.isIncome) }
    val noteState = rememberTextFieldState(initialText = split.note)

    LaunchedEffect(split.localId, split.amountMinor) {
        val updatedAmount = AmountScale.formatEditableAmount(split.amountMinor, currencyDecimals)
        if (amountState.text.toString() != updatedAmount) {
            amountState.setTextAndPlaceCursorAtEnd(updatedAmount)
        }
    }
    LaunchedEffect(split.localId) {
        if (noteState.text.toString() != split.note) {
            noteState.setTextAndPlaceCursorAtEnd(split.note)
        }
    }
    val selectedCategory = categories.firstOrNull { it.id == split.categoryId }
    val selectedProject = projects.firstOrNull { it.id == split.projectId && it.id > 0L }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (isNew) "Add Split Item" else "Edit Split Item",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("Expense") })
            FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("Income") })
        }
        Surface(
            shape = RoundedCornerShape(StitchDimens.RadiusTile),
            color = StitchColors.PillSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickCategory),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Category", fontSize = 11.sp, color = StitchColors.TextTertiary)
                Text(
                    text = selectedCategory?.fullPath ?: "Uncategorized",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        OutlinedTextField(
            state = amountState,
            label = { Text("Amount") },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = {
                IconButton(onClick = onOpenCalculator) {
                    Icon(Icons.Rounded.Calculate, contentDescription = "Calculator")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state = noteState,
            label = { Text("Note") },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            shape = RoundedCornerShape(StitchDimens.RadiusTile),
            color = StitchColors.PillSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickProject),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Project", fontSize = 11.sp, color = StitchColors.TextTertiary)
                Text(
                    text = selectedProject?.title ?: "No project",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, shape = CircleShape, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val minor = AmountScale.parseMinorUnits(amountState.text.toString(), currencyDecimals) ?: 0L
                    onSave(
                        split.copy(
                            amountMinor = minor,
                            isIncome = isIncome,
                            note = noteState.text.toString(),
                        )
                    )
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StitchColors.Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text("Apply")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun PlatformDatePickerEffect(
    dateTimeMillis: Long,
    onDateSelected: (year: Int, monthZeroBased: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(dateTimeMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateTimeMillis }
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day -> onDateSelected(year, month, day) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        )
        dialog.setOnCancelListener { onDismiss() }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose { if (dialog.isShowing) dialog.dismiss() }
    }
}

@Composable
fun PlatformTimePickerEffect(
    dateTimeMillis: Long,
    is24Hour: Boolean,
    onTimeSelected: (hourOfDay: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(dateTimeMillis, is24Hour) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateTimeMillis }
        val dialog = TimePickerDialog(
            context,
            { _, hour, minute -> onTimeSelected(hour, minute) },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            is24Hour,
        )
        dialog.setOnCancelListener { onDismiss() }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        onDispose { if (dialog.isShowing) dialog.dismiss() }
    }
}

@Composable
fun DiscardTransactionDialog(
    isAiDraft: Boolean,
    onConfirmDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(if (isAiDraft) "Discard transaction draft?" else "Discard unsaved changes?") },
        text = {
            Text(
                if (isAiDraft) {
                    "This transaction has not been saved yet. Any staged receipt photo will be removed."
                } else {
                    "Your unsaved edits will be lost."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDiscard) { Text("Discard") }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing") }
        },
    )
}

@Composable
fun ConsumedDraftAlertDialog(
    savedTransactionId: Long,
    onOpenSaved: () -> Unit,
    onCreateDuplicate: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Draft already saved") },
        text = {
            Text(
                "This assistant draft was already saved as transaction #$savedTransactionId. " +
                    "Would you like to edit the saved transaction or create another entry?"
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSaved) { Text("Edit saved") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Close") }
                TextButton(onClick = onCreateDuplicate) { Text("Create duplicate") }
            }
        },
    )
}

private val sampleCategories = listOf(
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
        parentId = 1L,
        title = "Restaurants",
        fullPath = "Food : Restaurants",
        level = 1,
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
    CategoryOption(
        id = 5L,
        parentId = 4L,
        title = "Fuel",
        fullPath = "Transport : Fuel",
        level = 1,
        isIncome = false,
    ),
)

@Preview(name = "Category Browser Sheet", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun CategoryBrowserSheetPreview() {
    TransactionStitchTheme {
        Surface(
            color = StitchColors.SurfaceWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CategoryBrowserSheetContent(
                categories = sampleCategories,
                selectedCategoryId = 2L,
                onSelect = {},
                onCreateCategory = {},
            )
        }
    }
}

private val sampleProjects = listOf(
    ProjectOption(id = 1L, title = "Home Renovation"),
    ProjectOption(id = 2L, title = "Vacation 2025"),
)

private val sampleSplit = EditableSplit(
    localId = 1L,
    categoryId = 2L,
    amountMinor = 1250L,
    isIncome = false,
    note = "Weekly groceries",
    projectId = 1L,
)

@Preview(name = "Split Item Editor Sheet", showBackground = true, backgroundColor = 0xFFFCFBFC)
@Composable
private fun SplitItemEditorSheetPreview() {
    TransactionStitchTheme {
        Surface(
            color = StitchColors.SurfaceWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            SplitItemEditorSheetContent(
                split = sampleSplit,
                isNew = false,
                categories = sampleCategories,
                projects = sampleProjects,
                currencyDecimals = 2,
                onPickCategory = {},
                onPickProject = {},
                onOpenCalculator = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
