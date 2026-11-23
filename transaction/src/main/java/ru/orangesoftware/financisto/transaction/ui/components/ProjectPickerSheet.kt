// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerSheet(
    projects: List<ProjectOption>,
    selectedProjectId: Long,
    pendingProjectName: String?,
    allowCreateInline: Boolean,
    onSelectProject: (Long) -> Unit,
    onCreatePendingProject: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        ProjectPickerSheetContent(
            projects = projects,
            selectedProjectId = selectedProjectId,
            pendingProjectName = pendingProjectName,
            allowCreateInline = allowCreateInline,
            onSelectProject = onSelectProject,
            onCreatePendingProject = onCreatePendingProject,
        )
    }
}

@Composable
fun ProjectPickerSheetContent(
    projects: List<ProjectOption>,
    selectedProjectId: Long,
    pendingProjectName: String?,
    allowCreateInline: Boolean,
    onSelectProject: (Long) -> Unit,
    onCreatePendingProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val newNameState = rememberTextFieldState(initialText = pendingProjectName.orEmpty())
    val canCreateProject by remember { computedStateOf { newNameState.text.toString().trim().isNotEmpty() } }
    val selectableProjects = remember(projects) { projects.filter { it.id > 0L } }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.tx_sheet_select_project),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (allowCreateInline) {
            ProjectInlineCreateRow(
                nameState = newNameState,
                canCreate = canCreateProject,
                onCreate = { onCreatePendingProject(newNameState.text.toString().trim()) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .selectableGroup(),
        ) {
            item(key = TransactionIds.NO_PROJECT_ID) {
                ProjectRadioRow(
                    title = stringResource(R.string.tx_placeholder_no_project),
                    selected = selectedProjectId == TransactionIds.NO_PROJECT_ID && pendingProjectName == null,
                    onClick = { onSelectProject(TransactionIds.NO_PROJECT_ID) },
                )
            }
            items(selectableProjects, key = { it.id }) { proj ->
                val title = if (proj.isActive) {
                    proj.title
                } else {
                    stringResource(R.string.tx_project_inactive_format, proj.title)
                }
                ProjectRadioRow(
                    title = title,
                    selected = proj.id == selectedProjectId && pendingProjectName == null,
                    onClick = { onSelectProject(proj.id) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ProjectInlineCreateRow(
    nameState: TextFieldState,
    canCreate: Boolean,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            state = nameState,
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text(stringResource(R.string.tx_placeholder_new_project)) },
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(enabled = canCreate, onClick = onCreate) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.tx_action_new))
        }
    }
}

@Composable
private fun ProjectRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(FinancistoTheme.dimens.RadiusTile)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(rowShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

private val SamplePickerProjects = listOf(
    ProjectOption(id = 1L, title = "Home Renovation"),
    ProjectOption(id = 2L, title = "Europe Trip"),
)

@Preview(name = "Project Picker Sheet - Light", showBackground = true)
@Composable
private fun ProjectPickerSheetPreview() {
    FinancistoTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ProjectPickerSheetContent(
                projects = SamplePickerProjects,
                selectedProjectId = 1L,
                pendingProjectName = null,
                allowCreateInline = true,
                onSelectProject = {},
                onCreatePendingProject = {},
            )
        }
    }
}

@Preview(name = "Project Picker Sheet - Dark", showBackground = true)
@Composable
private fun ProjectPickerSheetDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ProjectPickerSheetContent(
                projects = SamplePickerProjects,
                selectedProjectId = 2L,
                pendingProjectName = null,
                allowCreateInline = true,
                onSelectProject = {},
                onCreatePendingProject = {},
            )
        }
    }
}
