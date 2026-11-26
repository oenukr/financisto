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
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.orangesoftware.financisto.R
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
import ru.orangesoftware.financisto.utils.PinProtection
import timber.log.Timber

class ComposeTransactionActivity : ComponentActivity() {

    private val db: DatabaseAdapter by inject()
    private val receiptMediaPort: ReceiptMediaPort by inject()
    private val draftSessionStore: FinancistoDraftSessionStore by inject()
    private val ioDispatcher: CoroutineDispatcher by inject()

    private val launchArgs: TransactionLaunchArgs by lazy { TransactionEntryRouter.resolveLaunchArgs(intent) }

    @VisibleForTesting
    internal val viewModel: TransactionViewModel by viewModel { parametersOf(launchArgs) }

    @VisibleForTesting
    internal var pendingCameraFileName: String? = null

    @VisibleForTesting
    internal var pendingAiDraftPrompt by mutableStateOf<TransactionDraft?>(null)

    private var deferredIncomingAiDraft: TransactionDraft? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val fileName = pendingCameraFileName
        pendingCameraFileName = null
        if (fileName != null) {
            if (success) {
                dispatchIntentWhenReady(
                    scope = lifecycleScope,
                    viewModel = viewModel,
                    receiptMediaPort = receiptMediaPort,
                    intent = TransactionIntent.AttachCapturedPhotoFile(fileName),
                )
            } else {
                lifecycleScope.launch {
                    receiptMediaPort.deleteStagedFile(fileName)
                }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            dispatchIntentWhenReady(
                scope = lifecycleScope,
                viewModel = viewModel,
                receiptMediaPort = receiptMediaPort,
                intent = TransactionIntent.AttachReceiptFromUri(uri.toString()),
            )
        }
    }

    private val categoryCreatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getLongExtra(DatabaseHelper.CategoryColumns._id.name, -1L) ?: -1L
            if (result.resultCode == RESULT_OK && id > 0L) {
                dispatchIntentWhenReady(
                    scope = lifecycleScope,
                    viewModel = viewModel,
                    receiptMediaPort = receiptMediaPort,
                    intent = TransactionIntent.ExternalEntityCreated(ExternalEntityKind.CATEGORY, id),
                )
            }
        }

    private val locationCreatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getLongExtra(DatabaseHelper.EntityColumns.ID, -1L) ?: -1L
            if (result.resultCode == RESULT_OK && id > 0L) {
                dispatchIntentWhenReady(
                    scope = lifecycleScope,
                    viewModel = viewModel,
                    receiptMediaPort = receiptMediaPort,
                    intent = TransactionIntent.ExternalEntityCreated(ExternalEntityKind.LOCATION, id),
                )
            }
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        pendingCameraFileName = savedInstanceState?.getString(KEY_PENDING_CAMERA_FILE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.onPinStateChanged(!shouldLockScreen() || PinProtection.isUnlocked())
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect(::handleEffect)
            }
        }
        setContent {
            val uiState by viewModel.uiState
            val draftSession by draftSessionStore.sessionState
            LaunchedEffect(draftSession.pendingDraft?.draftId, uiState) {
                surfacePendingAiDraftIfReady(
                    pendingDraft = draftSession.pendingDraft,
                    uiState = uiState,
                )
            }
            TransactionScreen(
                uiState = uiState,
                resolveReceiptPath = receiptMediaPort::resolveFilePath,
                onIntent = viewModel::onIntent,
                onCloseRequested = {
                    if ((viewModel.uiState.value as? TransactionUiState.Ready)?.isSaving != true) {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                },
            )
            val promptDraft = pendingAiDraftPrompt
            val isSaving = (uiState as? TransactionUiState.Ready)?.isSaving == true
            if (promptDraft != null && uiState !is TransactionUiState.PinLockedMask) {
                PendingAiDraftDialog(
                    isSwitchEnabled = !isSaving,
                    onSwitchToDraft = {
                        trySwitchToPendingAiDraft(promptDraft, draftSessionStore)
                    },
                    onKeepEditing = {
                        dismissPendingAiDraft(promptDraft, draftSessionStore, receiptMediaPort)
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
        viewModel.onPinStateChanged(!shouldLockScreen() || PinProtection.isUnlocked())
        if (!isFinishing && (!shouldLockScreen() || PinProtection.isUnlocked())) {
            val deferred = deferredIncomingAiDraft
            deferredIncomingAiDraft = null
            val currentDraftId = activeAiDraftId(viewModel.uiState.value, launchArgs)
            val draftToProcess = deferred ?: draftSessionStore.peekPendingAutoOpenDraft(currentDraftId)
            draftToProcess?.let(::handleIncomingAiDraft)
        }
    }

    @VisibleForTesting
    internal fun surfacePendingAiDraftIfReady(
        pendingDraft: TransactionDraft?,
        uiState: TransactionUiState,
        unlocked: Boolean = !shouldLockScreen() || PinProtection.isUnlocked(),
    ) {
        val canSurface = unlocked &&
            uiState !is TransactionUiState.Loading &&
            uiState !is TransactionUiState.PinLockedMask &&
            !isFinishing
        if (pendingDraft != null && canSurface) {
            handleIncomingAiDraft(pendingDraft)
        }
    }

    private fun shouldLockScreen(): Boolean {
        return when (launchArgs) {
            is TransactionLaunchArgs.Edit,
            is TransactionLaunchArgs.AiDraft -> MyPreferences.isPinProtected(this)
            is TransactionLaunchArgs.BlankNew -> MyPreferences.isPinProtectedNewTransaction(this)
        }
    }

    private fun handleIncomingAiDraft(incomingDraft: TransactionDraft) {
        val unlocked = !shouldLockScreen() || PinProtection.isUnlocked()
        when {
            isFinishing -> Unit
            !unlocked -> deferredIncomingAiDraft = incomingDraft
            viewModel.uiState.value is TransactionUiState.Loading -> {
                deferredIncomingAiDraft = incomingDraft
                lifecycleScope.launch {
                    snapshotFlow { viewModel.uiState.value }.first { it !is TransactionUiState.Loading }
                    val deferred = deferredIncomingAiDraft
                    val unlockedNow = !shouldLockScreen() || PinProtection.isUnlocked()
                    if (deferred != null && !isFinishing && unlockedNow) {
                        deferredIncomingAiDraft = null
                        handleIncomingAiDraft(deferred)
                    }
                }
            }
            else -> when (resolveIncomingDraftAction(incomingDraft.draftId, viewModel, launchArgs)) {
                IncomingDraftResolution.ACKNOWLEDGE_CURRENT ->
                    draftSessionStore.clearPendingAutoOpenDraft(incomingDraft.draftId)
                IncomingDraftResolution.PROMPT_USER -> {
                    if (pendingAiDraftPrompt?.draftId != incomingDraft.draftId) {
                        pendingAiDraftPrompt = incomingDraft
                    }
                }
                IncomingDraftResolution.REPLACE_IMMEDIATELY -> {
                    trySwitchToPendingAiDraft(incomingDraft, draftSessionStore)
                }
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
                if (effect.args is TransactionLaunchArgs.AiDraft) {
                    Timber.w("Ignoring legacy editor launch for unsupported AI draft")
                    Toast.makeText(
                        this,
                        getString(R.string.tx_notice_ai_draft_legacy_unsupported),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    val isTransfer = resolveLegacyIsTransfer(db, effect.args, ioDispatcher)
                    val legacyIntent = TransactionEntryRouter.createLegacyEditorIntent(this, effect.args, isTransfer)
                    if (legacyIntent != null) {
                        legacyIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        startActivity(legacyIntent)
                        finish()
                    }
                }
            }
            is TransactionEffect.LaunchExternalCategoryCreator ->
                categoryCreatorLauncher.launch(Intent(this, CategoryActivity::class.java))
            is TransactionEffect.LaunchExternalLocationCreator ->
                locationCreatorLauncher.launch(Intent(this, LocationActivity::class.java))
            is TransactionEffect.LaunchCameraCapture -> {
                val targetFile = createCameraCaptureFile(ioDispatcher)
                if (targetFile == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.tx_error_camera_capture_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    pendingCameraFileName = targetFile.name
                    if (!launchCameraCaptureUri(this, targetFile, cameraLauncher::launch)) {
                        pendingCameraFileName = null
                        receiptMediaPort.deleteStagedFile(targetFile.name)
                        Toast.makeText(
                            this,
                            getString(R.string.tx_error_camera_capture_unavailable),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            is TransactionEffect.LaunchGalleryPick -> galleryLauncher.launch("image/*")
            is TransactionEffect.ShowMessage ->
                Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    internal enum class IncomingDraftResolution {
        ACKNOWLEDGE_CURRENT,
        PROMPT_USER,
        REPLACE_IMMEDIATELY,
    }

    internal companion object {
        const val KEY_PENDING_CAMERA_FILE = "pending_camera_file_name"
    }
}
