// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.Context
import android.content.Intent
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.TransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.transaction.model.TransactionDraft
import ru.orangesoftware.financisto.transaction.model.TransactionLaunchArgs
import ru.orangesoftware.financisto.transaction.model.TransactionMode

object TransactionEntryRouter {
    const val EXTRA_LAUNCH_ARGS = "transaction_launch_args"
    const val EXTRA_FORCE_LEGACY = "force_legacy_editor"

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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
    ): Intent {
        val targetClass = when (args) {
            is TransactionLaunchArgs.BlankNew ->
                if (args.initialMode == TransactionMode.TRANSFER) {
                    TransferActivity::class.java
                } else {
                    TransactionActivity::class.java
                }
            is TransactionLaunchArgs.Edit ->
                if (fallbackIsTransfer) TransferActivity::class.java else TransactionActivity::class.java
            is TransactionLaunchArgs.AiDraft ->
                if (args.draft.mode == TransactionMode.TRANSFER) {
                    TransferActivity::class.java
                } else {
                    TransactionActivity::class.java
                }
        }
        return Intent(context, targetClass).apply {
            putExtra(EXTRA_FORCE_LEGACY, true)
            when (args) {
                is TransactionLaunchArgs.BlankNew -> {
                    args.accountId?.let { putExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, it) }
                    args.initialDateTimeMillis?.let { putExtra(AbstractTransactionActivity.DATETIME_EXTRA, it) }
                }
                is TransactionLaunchArgs.Edit -> putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, args.transactionId)
                is TransactionLaunchArgs.AiDraft -> {
                    args.draft.fromAccountId?.let { putExtra(AbstractTransactionActivity.ACCOUNT_ID_EXTRA, it) }
                    putExtra(AbstractTransactionActivity.DATETIME_EXTRA, args.draft.dateTimeMillis)
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun launchPendingAiDraftIfPresent(context: Context, currentDraftId: String? = null): Boolean {
        val draft = FinancistoDraftSessionStore(context)
            .consumePendingAutoOpenDraft(currentDraftId)
            ?: return false
        context.startActivity(createAiDraftIntent(context, draft))
        return true
    }

    @JvmStatic
    fun shouldUseLegacyImmediately(intent: Intent?, db: DatabaseAdapter): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(EXTRA_FORCE_LEGACY, false)) return true
        if (intent.hasExtra(TransactionActivity.CURRENT_BALANCE_EXTRA)) return true
        if (intent.getBooleanExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, false)) return true
        if (intent.getBooleanExtra(AbstractTransactionActivity.NEW_FROM_TEMPLATE_EXTRA, false)) return true
        if (intent.getIntExtra(AbstractTransactionActivity.TEMPLATE_EXTRA, 0) > 0) return true
        val tranId = intent.getLongExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, -1L)
        if (tranId > 0L) {
            val loaded = db.getTransaction(tranId)
            val parent = if (loaded.isSplitChild) db.getTransaction(loaded.parentId) else loaded
            if (parent.id > 0L && parent.isTemplateLike) return true
        }
        return false
    }
}
