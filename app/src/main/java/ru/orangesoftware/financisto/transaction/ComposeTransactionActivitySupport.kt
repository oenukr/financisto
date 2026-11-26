// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.utils.PicturesUtil
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

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
        is TransactionUiState.LegacyRedirecting,
        is TransactionUiState.LoadError -> null
    }
}

internal fun resolveIncomingDraftAction(
    incomingDraftId: String,
    viewModel: TransactionViewModel,
    launchArgs: TransactionLaunchArgs,
): ComposeTransactionActivity.IncomingDraftResolution {
    val currentDraftId = activeAiDraftId(viewModel.uiState.value, launchArgs)
    val shouldPrompt = viewModel.hasUnsavedUserEdits() || when (val state = viewModel.uiState.value) {
        is TransactionUiState.Ready ->
            state.form.isAiDraft && state.activeSheet !is ActiveSheet.ConsumedDraftPrompt
        is TransactionUiState.Loading,
        is TransactionUiState.PinLockedMask -> launchArgs is TransactionLaunchArgs.AiDraft
        is TransactionUiState.NotFound,
        is TransactionUiState.NoAccounts,
        is TransactionUiState.LegacyRedirecting,
        is TransactionUiState.LoadError -> false
    }
    return when {
        incomingDraftId == currentDraftId ->
            ComposeTransactionActivity.IncomingDraftResolution.ACKNOWLEDGE_CURRENT
        shouldPrompt ->
            ComposeTransactionActivity.IncomingDraftResolution.PROMPT_USER
        else -> ComposeTransactionActivity.IncomingDraftResolution.REPLACE_IMMEDIATELY
    }
}

internal fun ComposeTransactionActivity.trySwitchToPendingAiDraft(
    draft: TransactionDraft,
    draftSessionStore: FinancistoDraftSessionStore,
): Boolean {
    if ((viewModel.uiState.value as? TransactionUiState.Ready)?.isSaving == true) {
        return false
    }
    pendingAiDraftPrompt = null
    draftSessionStore.clearPendingAutoOpenDraft(draft.draftId)
    viewModel.cleanupStagedReceipts()
    startActivity(TransactionEntryRouter.createAiDraftReplacementIntent(this, draft))
    finish()
    return true
}

internal fun ComposeTransactionActivity.dismissPendingAiDraft(
    draft: TransactionDraft,
    draftSessionStore: FinancistoDraftSessionStore,
    receiptMediaPort: ReceiptMediaPort,
) {
    pendingAiDraftPrompt = null
    draftSessionStore.clearPendingAutoOpenDraft(draft.draftId)
    val stagedFile = draft.stagedReceiptFileName?.takeIf { it.isNotBlank() } ?: return
    val currentForm = (viewModel.uiState.value as? TransactionUiState.Ready)?.form
    val matchesActiveReceipt = stagedFile == currentForm?.attachedPictureFileName ||
        stagedFile == currentForm?.initialPersistedPictureFileName
    if (!matchesActiveReceipt) {
        lifecycleScope.launch {
            if (draftSessionStore.getSavedTransactionId(draft.draftId) == null) {
                receiptMediaPort.deleteStagedFile(stagedFile)
            }
        }
    }
}

private val TransactionUiState.isIdleReady: Boolean
    get() = this is TransactionUiState.Ready && !isSaving

private val TransactionUiState.isSavingReady: Boolean
    get() = this is TransactionUiState.Ready && isSaving

private val TransactionUiState.isTerminalNonReady: Boolean
    get() = this is TransactionUiState.NotFound ||
        this is TransactionUiState.NoAccounts ||
        this is TransactionUiState.LegacyRedirecting

internal fun dispatchIntentWhenReady(
    scope: CoroutineScope,
    viewModel: TransactionViewModel,
    receiptMediaPort: ReceiptMediaPort,
    intent: TransactionIntent,
) {
    val initialState = viewModel.currentEditorState.value
    if (initialState.isIdleReady) {
        if (!viewModel.onIntent(intent) && intent is TransactionIntent.AttachCapturedPhotoFile) {
            scope.launch {
                withContext(NonCancellable) {
                    receiptMediaPort.deleteStagedFile(intent.fileName)
                }
            }
        }
        return
    }
    scope.launch {
        var dispatched = false
        var savingForm: TransactionFormState? =
            if (initialState.isSavingReady) (initialState as TransactionUiState.Ready).form else null
        try {
            val settled = snapshotFlow { viewModel.currentEditorState.value }.first { state ->
                if (state.isSavingReady) {
                    savingForm = savingForm ?: (state as TransactionUiState.Ready).form
                }
                state.isIdleReady || state.isTerminalNonReady
            }
            val shouldDispatch = settled is TransactionUiState.Ready &&
                (savingForm == null || settled.form == savingForm)
            if (shouldDispatch) {
                dispatched = viewModel.onIntent(intent)
            }
        } finally {
            if (!dispatched && intent is TransactionIntent.AttachCapturedPhotoFile) {
                withContext(NonCancellable) {
                    receiptMediaPort.deleteStagedFile(intent.fileName)
                }
            }
        }
    }
}

internal suspend fun createCameraCaptureFile(
    ioDispatcher: CoroutineDispatcher,
    createFile: () -> File = PicturesUtil::createEmptyImageFile,
): File? {
    val createdFileRef = AtomicReference<File?>(null)
    return try {
        withContext(ioDispatcher) {
            createFile().also { createdFileRef.set(it) }
        }
    } catch (e: CancellationException) {
        createdFileRef.getAndSet(null)?.let { leakedFile ->
            withContext(NonCancellable + ioDispatcher) {
                deleteCameraFileQuietly(leakedFile)
            }
        }
        throw e
    } catch (e: IOException) {
        Timber.e(e, "Failed to create image file for camera capture")
        null
    } catch (e: SecurityException) {
        Timber.e(e, "No permission to create image file for camera capture")
        null
    }
}

private fun deleteCameraFileQuietly(file: File?) {
    if (file == null) return
    try {
        if (file.exists() && !file.delete()) {
            Timber.w("Failed to delete cancelled camera capture file: %s", file.absolutePath)
        }
    } catch (e: SecurityException) {
        Timber.w(e, "No permission to delete cancelled camera capture file: %s", file.absolutePath)
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
    isSwitchEnabled: Boolean = true,
) {
    FinancistoTheme {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text(stringResource(R.string.tx_dialog_pending_ai_draft_title)) },
            text = { Text(stringResource(R.string.tx_dialog_pending_ai_draft_body)) },
            confirmButton = {
                TextButton(
                    onClick = onSwitchToDraft,
                    enabled = isSwitchEnabled,
                ) {
                    Text(stringResource(R.string.tx_dialog_pending_ai_draft_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = onKeepEditing) {
                    Text(stringResource(R.string.tx_dialog_pending_ai_draft_keep))
                }
            },
        )
    }
}
