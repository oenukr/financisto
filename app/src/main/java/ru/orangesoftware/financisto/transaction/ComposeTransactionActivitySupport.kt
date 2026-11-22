// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode

internal suspend fun resolveLegacyIsTransfer(
    db: DatabaseAdapter,
    args: TransactionLaunchArgs,
    ioDispatcher: CoroutineDispatcher,
): Boolean {
    return when (args) {
        is TransactionLaunchArgs.Edit -> withContext(ioDispatcher) {
            val loaded = db.getTransaction(args.transactionId)
            val parent = if (loaded.isSplitChild) db.getTransaction(loaded.parentId) else loaded
            parent.isTransfer
        }
        is TransactionLaunchArgs.BlankNew -> args.initialMode == TransactionMode.TRANSFER
        is TransactionLaunchArgs.AiDraft -> args.draft.mode == TransactionMode.TRANSFER
    }
}

@Composable
internal fun PendingAiDraftDialog(
    onSwitchToDraft: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    FinancistoTheme {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text("Open new transaction draft?") },
            text = { Text("A new AI transaction draft is ready. Switching will discard your unsaved edits.") },
            confirmButton = {
                TextButton(onClick = onSwitchToDraft) {
                    Text("Switch to draft")
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepEditing) {
                    Text("Keep editing")
                }
            },
        )
    }
}
