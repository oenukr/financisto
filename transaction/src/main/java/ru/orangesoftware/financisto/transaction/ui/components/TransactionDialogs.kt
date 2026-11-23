// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import ru.orangesoftware.financisto.transaction.R
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDatePickerDialog(
    dateTimeMillis: Long,
    onDateSelected: (year: Int, monthZeroBased: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemZone = remember { TimeZone.currentSystemDefault() }
    val storedDate = remember(dateTimeMillis, systemZone) {
        Instant.fromEpochMilliseconds(dateTimeMillis)
            .toLocalDateTime(systemZone)
            .date
    }
    val initialUtcDateMillis = remember(storedDate) {
        storedDate
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds()
    }
    val yearRange = remember(storedDate.year) {
        val minYear = minOf(DatePickerDefaults.YearRange.first, storedDate.year)
        val maxYear = maxOf(DatePickerDefaults.YearRange.last, storedDate.year)
        minYear..maxYear
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcDateMillis,
        yearRange = yearRange,
    )

    DatePickerDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = datePickerState.selectedDateMillis != null,
                onClick = {
                    val pickedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val pickedDate = Instant.fromEpochMilliseconds(pickedMillis)
                        .toLocalDateTime(TimeZone.UTC)
                        .date
                    onDateSelected(pickedDate.year, pickedDate.month.number - 1, pickedDate.day)
                },
            ) {
                Text(stringResource(R.string.tx_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tx_action_cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTimePickerDialog(
    dateTimeMillis: Long,
    is24Hour: Boolean,
    onTimeSelected: (hourOfDay: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemZone = remember { TimeZone.currentSystemDefault() }
    val initialTime = remember(dateTimeMillis, systemZone) {
        Instant.fromEpochMilliseconds(dateTimeMillis).toLocalDateTime(systemZone).time
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = is24Hour,
    )

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tx_dialog_select_time)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(R.string.tx_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tx_action_cancel))
            }
        },
    )
}

@Composable
fun DiscardTransactionDialog(
    isAiDraft: Boolean,
    onConfirmDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onKeepEditing,
        title = {
            Text(
                stringResource(
                    if (isAiDraft) R.string.tx_dialog_discard_draft_title else R.string.tx_dialog_discard_changes_title,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (isAiDraft) R.string.tx_dialog_discard_draft_body else R.string.tx_dialog_discard_changes_body,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDiscard) {
                Text(stringResource(R.string.tx_action_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.tx_action_keep_editing))
            }
        },
    )
}

@Composable
fun ConsumedDraftAlertDialog(
    savedTransactionId: Long,
    onOpenSaved: () -> Unit,
    onCreateDuplicate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tx_dialog_consumed_draft_title)) },
        text = { Text(stringResource(R.string.tx_dialog_consumed_draft_body, savedTransactionId)) },
        confirmButton = {
            TextButton(onClick = onOpenSaved) {
                Text(stringResource(R.string.tx_action_edit_saved))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.tx_action_close))
                }
                TextButton(onClick = onCreateDuplicate) {
                    Text(stringResource(R.string.tx_action_create_duplicate))
                }
            }
        },
    )
}
