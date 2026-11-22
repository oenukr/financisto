// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.domain.AmountScale
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    title: String,
    accounts: List<AccountOption>,
    selectedAccountId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val queryState = rememberTextFieldState()
    val filtered by remember(accounts) {
        computedStateOf {
            val needle = queryState.text.toString().trim().lowercase()
            if (needle.isEmpty()) accounts else accounts.filter { it.title.lowercase().contains(needle) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = StitchColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                state = queryState,
                lineLimits = TextFieldLineLimits.SingleLine,
                placeholder = { Text("Search account...") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(filtered, key = { it.id }) { acc ->
                    val bal = AmountScale.formatDisplayAmount(
                        minorUnits = acc.balanceMinor,
                        decimals = acc.currencyDecimals,
                        symbol = acc.currencySymbol.ifBlank { acc.currencyCode },
                        includeSign = acc.balanceMinor < 0L,
                    )
                    SelectableSheetRow(
                        title = if (acc.isActive) acc.title else "${acc.title} (Archived)",
                        subtitle = "${acc.typeLabel} • $bal",
                        selected = acc.id == selectedAccountId,
                        onClick = { onSelect(acc.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    currencies: List<CurrencyOption>,
    accountCurrencyCode: String,
    selectedOriginalCurrencyId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Transaction Currency", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SelectableSheetRow(
                title = "Same as account ($accountCurrencyCode)",
                subtitle = "No currency conversion",
                selected = selectedOriginalCurrencyId == null,
                onClick = { onSelect(null) },
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(currencies, key = { it.id }) { currency ->
                    SelectableSheetRow(
                        title = "${currency.code} (${currency.symbol})",
                        subtitle = "${currency.decimals} decimal places",
                        selected = selectedOriginalCurrencyId == currency.id,
                        onClick = { onSelect(currency.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPickerSheet(
    selectedStatus: TransactionStatusOption,
    onSelect: (TransactionStatusOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Transaction Status", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            TransactionStatusOption.entries.forEach { option ->
                SelectableSheetRow(
                    title = option.label,
                    subtitle = "",
                    selected = option == selectedStatus,
                    onClick = { onSelect(option) },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

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
) {
    val newNameState = rememberTextFieldState(initialText = pendingProjectName.orEmpty())
    val canCreateProject by remember {
        computedStateOf { newNameState.text.toString().trim().isNotEmpty() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StitchColors.SurfaceWhite) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Select Project", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (allowCreateInline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        state = newNameState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        placeholder = { Text("New project name") },
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        enabled = canCreateProject,
                        onClick = { onCreatePendingProject(newNameState.text.toString().trim()) },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text(" New")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            SelectableSheetRow(
                title = "No project",
                subtitle = "",
                selected = selectedProjectId == TransactionIds.NO_PROJECT_ID && pendingProjectName == null,
                onClick = { onSelectProject(TransactionIds.NO_PROJECT_ID) },
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(projects.filter { it.id > 0L }, key = { it.id }) { proj ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProject(proj.id) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (proj.isActive) proj.title else "${proj.title} (Inactive)",
                            fontSize = 14.sp,
                            color = StitchColors.TextPrimary,
                        )
                        RadioButton(
                            selected = proj.id == selectedProjectId && pendingProjectName == null,
                            onClick = { onSelectProject(proj.id) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectableSheetRow(
    title: String,
    subtitle: String,
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                if (subtitle.isNotEmpty()) {
                    Text(text = subtitle, fontSize = 11.sp, color = StitchColors.TextTertiary)
                }
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = StitchColors.Primary)
            }
        }
    }
}
