// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.CategoryActivity
import ru.orangesoftware.financisto.activity.LocationActivity
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ExternalEntityKind
import ru.orangesoftware.financisto.transaction.mvi.TransactionEffect
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionViewModel
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.ui.TransactionScreen
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PicturesUtil
import ru.orangesoftware.financisto.utils.PinProtection

class ComposeTransactionActivity : ComponentActivity() {

    private val db: DatabaseAdapter by inject()
    private val receiptMediaPort: ReceiptMediaPort by inject()

    private val launchArgs: TransactionLaunchArgs by lazy { resolveLaunchArgs(intent) }
    private val viewModel: TransactionViewModel by viewModel { parametersOf(launchArgs) }

    private var pendingCameraFileName: String? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val fileName = pendingCameraFileName
        pendingCameraFileName = null
        if (success && fileName != null) {
            viewModel.onIntent(TransactionIntent.AttachCapturedPhotoFile(fileName))
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
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            syncPinLockState()
        }
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.effects.collect(::handleEffect)
            }
            TransactionScreen(
                uiState = uiState,
                resolveReceiptPath = receiptMediaPort::resolveFilePath,
                onIntent = viewModel::onIntent,
                onCloseRequested = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
            )
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
        if (!shouldLockScreen() || PinProtection.isUnlocked()) {
            val currentDraftId = (launchArgs as? TransactionLaunchArgs.AiDraft)?.draft?.draftId
            if (TransactionEntryRouter.launchPendingAiDraftIfPresent(this, currentDraftId)) {
                finish()
                return
            }
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

    private fun handleEffect(effect: TransactionEffect) {
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
                val isTransfer = resolveLegacyIsTransfer(effect.args)
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
                val targetFile = PicturesUtil.createEmptyImageFile()
                pendingCameraFileName = targetFile.name
                val uri = FileProvider.getUriForFile(this, packageName, targetFile)
                cameraLauncher.launch(uri)
            }
            is TransactionEffect.LaunchGalleryPick -> galleryLauncher.launch("image/*")
            is TransactionEffect.ShowMessage ->
                Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveLegacyIsTransfer(args: TransactionLaunchArgs): Boolean {
        return when (args) {
            is TransactionLaunchArgs.Edit -> {
                val loaded = db.getTransaction(args.transactionId)
                val parent = if (loaded.isSplitChild) db.getTransaction(loaded.parentId) else loaded
                parent.isTransfer
            }
            is TransactionLaunchArgs.BlankNew -> args.initialMode == TransactionMode.TRANSFER
            is TransactionLaunchArgs.AiDraft -> args.draft.mode == TransactionMode.TRANSFER
        }
    }

    private fun resolveLaunchArgs(intent: Intent?): TransactionLaunchArgs {
        val explicit = intent?.let {
            IntentCompat.getParcelableExtra(
                it,
                TransactionEntryRouter.EXTRA_LAUNCH_ARGS,
                TransactionLaunchArgs::class.java,
            )
        }
        if (explicit != null) return explicit
        val tranId = intent?.getLongExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, -1L) ?: -1L
        if (tranId > 0L) return TransactionLaunchArgs.Edit(tranId)
        val accountId = intent?.getLongExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, -1L)?.takeIf { it > 0L }
        val dateMillis = intent?.getLongExtra(AbstractTransactionActivity.DATETIME_EXTRA, -1L)?.takeIf { it > 0L }
        return TransactionLaunchArgs.BlankNew(
            accountId = accountId,
            initialMode = TransactionMode.EXPENSE,
            initialDateTimeMillis = dateMillis,
        )
    }
}
