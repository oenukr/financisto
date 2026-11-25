// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import org.koin.core.context.GlobalContext
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.TransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import java.util.concurrent.atomic.AtomicLong

object TransactionEntryRouter {
    const val EXTRA_LAUNCH_ARGS = "transaction_launch_args"
    const val EXTRA_FORCE_LEGACY = "force_legacy_editor"
    private val savedTransactionVersionCounter = AtomicLong(0L)

    val savedTransactionVersion: Long
        get() = savedTransactionVersionCounter.get()

    internal fun notifyTransactionSaved() {
        savedTransactionVersionCounter.incrementAndGet()
    }

    @JvmStatic
    fun createNewIntent(
        context: Context,
        accountId: Long? = null,
        mode: TransactionMode = TransactionMode.EXPENSE,
    ): Intent {
        return Intent(context, ComposeTransactionActivity::class.java).apply {
            putExtra(
                EXTRA_LAUNCH_ARGS,
                TransactionLaunchArgs.BlankNew(accountId = accountId?.takeIf { it > 0L }, initialMode = mode),
            )
        }
    }

    @JvmStatic
    fun createEditIntent(context: Context, transactionId: Long): Intent {
        return Intent(context, ComposeTransactionActivity::class.java).apply {
            putExtra(EXTRA_LAUNCH_ARGS, TransactionLaunchArgs.Edit(transactionId))
        }
    }

    @JvmStatic
    fun createAiDraftIntent(context: Context, draft: TransactionDraft): Intent {
        return Intent(context, ComposeTransactionActivity::class.java).apply {
            putExtra(EXTRA_LAUNCH_ARGS, TransactionLaunchArgs.AiDraft(draft))
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
    }

    internal fun createAiDraftReplacementIntent(context: Context, draft: TransactionDraft): Intent {
        return Intent(context, ComposeTransactionActivity::class.java).apply {
            putExtra(EXTRA_LAUNCH_ARGS, TransactionLaunchArgs.AiDraft(draft))
            addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        }
    }

    @JvmStatic
    fun createForwardedComposeIntent(
        context: Context,
        sourceIntent: Intent?,
        isTransfer: Boolean,
    ): Intent {
        val tranId = sourceIntent?.getLongExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, -1L) ?: -1L
        val args: TransactionLaunchArgs = if (tranId > 0L) {
            TransactionLaunchArgs.Edit(tranId)
        } else {
            val accountId = sourceIntent?.getLongExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, -1L)
                ?.takeIf { it > 0L }
            val dateMillis = sourceIntent?.getLongExtra(AbstractTransactionActivity.DATETIME_EXTRA, -1L)
                ?.takeIf { it > 0L }
            TransactionLaunchArgs.BlankNew(
                accountId = accountId,
                initialMode = if (isTransfer) TransactionMode.TRANSFER else TransactionMode.EXPENSE,
                initialDateTimeMillis = dateMillis,
            )
        }
        return Intent(context, ComposeTransactionActivity::class.java).apply {
            putExtra(EXTRA_LAUNCH_ARGS, args)
            addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        }
    }

    @JvmStatic
    fun createLegacyEditorIntent(
        context: Context,
        args: TransactionLaunchArgs,
        fallbackIsTransfer: Boolean = false,
    ): Intent? {
        return when (args) {
            is TransactionLaunchArgs.AiDraft -> null
            is TransactionLaunchArgs.BlankNew -> {
                val targetClass = if (args.initialMode == TransactionMode.TRANSFER) {
                    TransferActivity::class.java
                } else {
                    TransactionActivity::class.java
                }
                Intent(context, targetClass).apply {
                    putExtra(EXTRA_FORCE_LEGACY, true)
                    args.accountId?.let { putExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, it) }
                    args.initialDateTimeMillis?.let { putExtra(AbstractTransactionActivity.DATETIME_EXTRA, it) }
                }
            }
            is TransactionLaunchArgs.Edit -> {
                val targetClass = if (fallbackIsTransfer) {
                    TransferActivity::class.java
                } else {
                    TransactionActivity::class.java
                }
                Intent(context, targetClass).apply {
                    putExtra(EXTRA_FORCE_LEGACY, true)
                    putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, args.transactionId)
                }
            }
        }
    }

    internal fun resolveLaunchArgs(intent: Intent?): TransactionLaunchArgs {
        val explicit = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_LAUNCH_ARGS, TransactionLaunchArgs::class.java)
        }
        val tranId = intent?.getLongExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, -1L) ?: -1L
        return when {
            explicit != null -> explicit
            tranId > 0L -> TransactionLaunchArgs.Edit(tranId)
            else -> {
                val accountId = intent?.getLongExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, -1L)
                    ?.takeIf { it > 0L }
                val dateMillis = intent?.getLongExtra(AbstractTransactionActivity.DATETIME_EXTRA, -1L)
                    ?.takeIf { it > 0L }
                TransactionLaunchArgs.BlankNew(
                    accountId = accountId,
                    initialMode = TransactionMode.EXPENSE,
                    initialDateTimeMillis = dateMillis,
                )
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun launchPendingAiDraftIfPresent(
        context: Context,
        currentDraftId: String? = null,
        draftSessionStore: FinancistoDraftSessionStore =
            GlobalContext.getOrNull()?.getOrNull<FinancistoDraftSessionStore>()
                ?: FinancistoDraftSessionStore(context.applicationContext),
    ): Boolean {
        val draft = draftSessionStore.consumePendingAutoOpenDraft(currentDraftId) ?: return false
        context.startActivity(createAiDraftIntent(context, draft))
        return true
    }

    @JvmStatic
    fun shouldUseLegacyImmediately(intent: Intent?, db: DatabaseAdapter): Boolean {
        if (intent == null) return false
        val hasAmountOrBalanceExtra = intent.hasExtra(TransactionActivity.CURRENT_BALANCE_EXTRA) ||
            intent.hasExtra(TransactionActivity.AMOUNT_EXTRA)
        val hasLegacyFlag = intent.getBooleanExtra(EXTRA_FORCE_LEGACY, false) ||
            hasAmountOrBalanceExtra ||
            intent.getBooleanExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, false)
        val hasTemplateExtra = intent.getBooleanExtra(AbstractTransactionActivity.NEW_FROM_TEMPLATE_EXTRA, false) ||
            intent.getIntExtra(AbstractTransactionActivity.TEMPLATE_EXTRA, 0) > 0
        val tranId = intent.getLongExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, -1L)
        val isTemplateTransaction = tranId > 0L && run {
            val loaded = db.getTransaction(tranId)
            val parent = if (loaded.isSplitChild) db.getTransaction(loaded.parentId) else loaded
            parent.id > 0L && parent.isTemplateLike
        }
        return hasLegacyFlag || hasTemplateExtra || isTemplateTransaction
    }
}
