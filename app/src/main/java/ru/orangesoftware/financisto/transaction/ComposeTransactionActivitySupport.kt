// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.utils.PicturesUtil
import timber.log.Timber
import java.io.File
import java.io.IOException

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

internal fun activeAiDraftId(
    uiState: TransactionUiState,
    launchArgs: TransactionLaunchArgs,
): String? {
    return when (uiState) {
        is TransactionUiState.Ready -> uiState.form.aiDraftId
        is TransactionUiState.Loading,
        is TransactionUiState.PinLockedMask -> (launchArgs as? TransactionLaunchArgs.AiDraft)?.draft?.draftId
        is TransactionUiState.NotFound,
        is TransactionUiState.NoAccounts,
        is TransactionUiState.LegacyRedirecting -> null
    }
}

internal fun shouldPromptBeforeReplacingWithDraft(
    viewModel: TransactionViewModel,
    launchArgs: TransactionLaunchArgs,
): Boolean {
    if (viewModel.hasUnsavedUserEdits()) return true
    return when (val state = viewModel.uiState.value) {
        is TransactionUiState.Ready ->
            state.form.isAiDraft && state.activeSheet !is ActiveSheet.ConsumedDraftPrompt
        is TransactionUiState.Loading,
        is TransactionUiState.PinLockedMask -> launchArgs is TransactionLaunchArgs.AiDraft
        is TransactionUiState.NotFound,
        is TransactionUiState.NoAccounts,
        is TransactionUiState.LegacyRedirecting -> false
    }
}

internal fun resolveIncomingDraftAction(
    incomingDraftId: String,
    viewModel: TransactionViewModel,
    launchArgs: TransactionLaunchArgs,
): ComposeTransactionActivity.IncomingDraftResolution {
    val currentDraftId = activeAiDraftId(viewModel.uiState.value, launchArgs)
    return when {
        incomingDraftId == currentDraftId ->
            ComposeTransactionActivity.IncomingDraftResolution.ACKNOWLEDGE_CURRENT
        shouldPromptBeforeReplacingWithDraft(viewModel, launchArgs) ->
            ComposeTransactionActivity.IncomingDraftResolution.PROMPT_USER
        else -> ComposeTransactionActivity.IncomingDraftResolution.REPLACE_IMMEDIATELY
    }
}

internal fun dispatchIntentWhenReady(
    scope: CoroutineScope,
    viewModel: TransactionViewModel,
    receiptMediaPort: ReceiptMediaPort,
    intent: TransactionIntent,
) {
    if (viewModel.uiState.value is TransactionUiState.Ready) {
        viewModel.onIntent(intent)
    } else {
        scope.launch {
            val settled = viewModel.uiState.first { state ->
                state is TransactionUiState.Ready ||
                    state is TransactionUiState.NotFound ||
                    state is TransactionUiState.NoAccounts ||
                    state is TransactionUiState.LegacyRedirecting
            }
            if (settled is TransactionUiState.Ready) {
                viewModel.onIntent(intent)
            } else if (intent is TransactionIntent.AttachCapturedPhotoFile) {
                receiptMediaPort.deleteStagedFile(intent.fileName)
            }
        }
    }
}

internal suspend fun createCameraCaptureFile(ioDispatcher: CoroutineDispatcher): File? {
    return try {
        withContext(ioDispatcher) { PicturesUtil.createEmptyImageFile() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Timber.e(e, "Failed to create image file for camera capture")
        null
    } catch (e: SecurityException) {
        Timber.e(e, "No permission to create image file for camera capture")
        null
    }
}

internal fun launchCameraCaptureUri(
    context: Context,
    targetFile: File,
    launch: (Uri) -> Unit,
): Boolean {
    return try {
        val uri = FileProvider.getUriForFile(context, context.packageName, targetFile)
        launch(uri)
        true
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No camera app available to capture receipt")
        false
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Failed to resolve FileProvider URI for camera capture")
        false
    } catch (e: SecurityException) {
        Timber.e(e, "Permission denied launching camera capture")
        false
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
            text = {
                Text(
                    "A new AI transaction draft is ready. " +
                        "Switching will discard your current draft or unsaved edits."
                )
            },
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
