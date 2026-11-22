// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.orangesoftware.financisto.activity.CategoryActivity
import ru.orangesoftware.financisto.activity.LocationActivity
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.mvi.ExternalEntityKind
import ru.orangesoftware.financisto.transaction.mvi.TransactionEffect
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.ui.TransactionScreen
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PicturesUtil
import ru.orangesoftware.financisto.utils.PinProtection
import timber.log.Timber
import java.io.IOException

class ComposeTransactionActivity : ComponentActivity() {

    private val db: DatabaseAdapter by inject()
    private val receiptMediaPort: ReceiptMediaPort by inject()
    private val draftSessionStore: FinancistoDraftSessionStore by inject()
    private val ioDispatcher: CoroutineDispatcher by inject()

    private val launchArgs: TransactionLaunchArgs by lazy { TransactionEntryRouter.resolveLaunchArgs(intent) }
    private val viewModel: TransactionViewModel by viewModel { parametersOf(launchArgs) }

    private var pendingCameraFileName: String? = null
    private var pendingAiDraftPrompt by mutableStateOf<TransactionDraft?>(null)

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val fileName = pendingCameraFileName
        pendingCameraFileName = null
        if (fileName != null) {
            if (success) {
                viewModel.onIntent(TransactionIntent.AttachCapturedPhotoFile(fileName))
            } else {
                lifecycleScope.launch {
                    receiptMediaPort.deleteStagedFile(fileName)
                }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.onIntent(TransactionIntent.AttachReceiptFromUri(uri.toString()))
        }
    }

    private val categoryCreatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getLongExtra(DatabaseHelper.CategoryColumns._id.name, -1L) ?: -1L
            if (result.resultCode == RESULT_OK && id > 0L) {
                viewModel.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, id))
            }
        }

    private val locationCreatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getLongExtra(DatabaseHelper.EntityColumns.ID, -1L) ?: -1L
            if (result.resultCode == RESULT_OK && id > 0L) {
                viewModel.onIntent(TransactionIntent.ExternalEntityCreated(ExternalEntityKind.LOCATION, id))
            }
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingCameraFileName = savedInstanceState?.getString(KEY_PENDING_CAMERA_FILE)
        syncPinLockState()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect(::handleEffect)
            }
        }
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            TransactionScreen(
                uiState = uiState,
                resolveReceiptPath = receiptMediaPort::resolveFilePath,
                onIntent = viewModel::onIntent,
                onCloseRequested = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
            )
            val promptDraft = pendingAiDraftPrompt
            if (promptDraft != null && uiState !is TransactionUiState.PinLockedMask) {
                PendingAiDraftDialog(
                    onSwitchToDraft = {
                        pendingAiDraftPrompt = null
                        val currentDraftId = (launchArgs as? TransactionLaunchArgs.AiDraft)?.draft?.draftId
                        draftSessionStore.consumePendingAutoOpenDraft(currentDraftId)
                        viewModel.cleanupStagedReceipts()
                        startActivity(TransactionEntryRouter.createAiDraftReplacementIntent(this, promptDraft))
                        finish()
                    },
                    onKeepEditing = {
                        pendingAiDraftPrompt = null
                        lifecycleScope.launch {
                            draftSessionStore.clearPendingDraft(promptDraft.draftId)
                        }
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_CAMERA_FILE, pendingCameraFileName)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incoming = TransactionEntryRouter.resolveLaunchArgs(intent)
        if (incoming is TransactionLaunchArgs.AiDraft) {
            handleIncomingAiDraft(incoming.draft)
        }
    }

    override fun onPause() {
        super.onPause()
        if (shouldLockScreen()) {
            PinProtection.lock(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldLockScreen()) {
            PinProtection.unlock(this)
        }
        syncPinLockState()
        if (!isFinishing && (!shouldLockScreen() || PinProtection.isUnlocked())) {
            val currentDraftId = (launchArgs as? TransactionLaunchArgs.AiDraft)?.draft?.draftId
            draftSessionStore.peekPendingAutoOpenDraft(currentDraftId)?.let(::handleIncomingAiDraft)
        }
    }

    private fun syncPinLockState() {
        val unlocked = !shouldLockScreen() || PinProtection.isUnlocked()
        viewModel.onPinStateChanged(unlocked)
    }

    private fun shouldLockScreen(): Boolean {
        return when (launchArgs) {
            is TransactionLaunchArgs.Edit,
            is TransactionLaunchArgs.AiDraft -> MyPreferences.isPinProtected(this)
            is TransactionLaunchArgs.BlankNew -> MyPreferences.isPinProtectedNewTransaction(this)
        }
    }

    private fun handleIncomingAiDraft(incomingDraft: TransactionDraft) {
        val currentDraftId = (launchArgs as? TransactionLaunchArgs.AiDraft)?.draft?.draftId
        when {
            incomingDraft.draftId == currentDraftId -> {
                lifecycleScope.launch { draftSessionStore.acknowledgeDraftOpened(incomingDraft.draftId) }
            }
            viewModel.hasUnsavedUserEdits() -> {
                if (pendingAiDraftPrompt?.draftId != incomingDraft.draftId) {
                    pendingAiDraftPrompt = incomingDraft
                }
            }
            else -> {
                pendingAiDraftPrompt = null
                draftSessionStore.consumePendingAutoOpenDraft(currentDraftId)
                viewModel.cleanupStagedReceipts()
                startActivity(TransactionEntryRouter.createAiDraftReplacementIntent(this, incomingDraft))
                finish()
            }
        }
    }

    private suspend fun handleEffect(effect: TransactionEffect) {
        when (effect) {
            is TransactionEffect.SavedAndFinish -> {
                val data = Intent().putExtra(DatabaseHelper.TransactionColumns._id.name, effect.transactionId)
                setResult(RESULT_OK, data)
                finish()
            }
            is TransactionEffect.CloseCancelled -> {
                setResult(RESULT_CANCELED)
                finish()
            }
            is TransactionEffect.OpenLegacyEditor -> {
                val isTransfer = resolveLegacyIsTransfer(db, effect.args, ioDispatcher)
                val legacyIntent = TransactionEntryRouter.createLegacyEditorIntent(this, effect.args, isTransfer)
                legacyIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                startActivity(legacyIntent)
                finish()
            }
            is TransactionEffect.LaunchExternalCategoryCreator ->
                categoryCreatorLauncher.launch(Intent(this, CategoryActivity::class.java))
            is TransactionEffect.LaunchExternalLocationCreator ->
                locationCreatorLauncher.launch(Intent(this, LocationActivity::class.java))
            is TransactionEffect.LaunchCameraCapture -> {
                val targetFile = try {
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
                if (targetFile != null) {
                    pendingCameraFileName = targetFile.name
                    val uri = FileProvider.getUriForFile(this, packageName, targetFile)
                    cameraLauncher.launch(uri)
                } else {
                    Toast.makeText(this, "Unable to prepare camera capture", Toast.LENGTH_SHORT).show()
                }
            }
            is TransactionEffect.LaunchGalleryPick -> galleryLauncher.launch("image/*")
            is TransactionEffect.ShowMessage ->
                Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val KEY_PENDING_CAMERA_FILE = "pending_camera_file_name"
    }
}
