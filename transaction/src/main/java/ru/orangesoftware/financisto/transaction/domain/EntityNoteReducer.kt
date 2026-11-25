// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

internal object EntityNoteReducer {

    fun reduce(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.UpdatePayeeQuery,
            is TransactionIntent.SelectPayee,
            is TransactionIntent.CreatePendingPayee,
            is TransactionIntent.ClearPayee -> reducePayee(state, intent)
            else -> reduceLocationAndNote(state, intent)
        }
    }

    private fun reducePayee(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.UpdatePayeeQuery -> state.copy(
                form = state.form.copy(
                    payeeQuery = intent.query,
                    payeeId = TransactionIds.NO_PAYEE_ID,
                    pendingPayeeName = intent.query.trim().takeIf { it.isNotEmpty() },
                )
            )
            is TransactionIntent.SelectPayee -> selectPayee(state, intent.payeeId)
            is TransactionIntent.CreatePendingPayee -> state.copy(
                form = state.form.copy(
                    payeeId = TransactionIds.NO_PAYEE_ID,
                    pendingPayeeName = intent.name.trim(),
                    payeeQuery = intent.name.trim(),
                ),
                expandedField = null,
            )
            is TransactionIntent.ClearPayee -> state.copy(
                form = state.form.copy(
                    payeeId = TransactionIds.NO_PAYEE_ID,
                    pendingPayeeName = null,
                    payeeQuery = "",
                )
            )
            else -> state
        }
    }

    private fun reduceLocationAndNote(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.ToggleInlineField -> state.copy(
                expandedField = if (state.expandedField == intent.field) null else intent.field
            )
            is TransactionIntent.CollapseInlineField -> state.copy(expandedField = null)
            is TransactionIntent.UpdateLocationQuery -> state.copy(
                form = state.form.copy(locationQuery = intent.query)
            )
            is TransactionIntent.SelectLocation -> selectLocation(state, intent.locationId)
            is TransactionIntent.ClearLocation -> state.copy(
                form = state.form.copy(locationId = TransactionIds.CURRENT_LOCATION_ID, locationQuery = "")
            )
            is TransactionIntent.UpdateNote -> state.copy(form = state.form.copy(note = intent.text))
            is TransactionIntent.SelectRecentNote -> state.copy(form = state.form.copy(note = intent.text))
            is TransactionIntent.ClearNote -> state.copy(form = state.form.copy(note = ""))
            else -> state
        }
    }

    private fun selectLocation(state: TransactionUiState.Ready, locationId: Long): TransactionUiState.Ready {
        val loc = state.catalog.locations.firstOrNull { it.id == locationId }
        return state.copy(
            form = state.form.copy(
                locationId = locationId,
                locationQuery = loc?.title.orEmpty(),
            ),
            expandedField = null,
        )
    }

    private fun selectPayee(state: TransactionUiState.Ready, payeeId: Long): TransactionUiState.Ready {
        val payee = state.catalog.payees.firstOrNull { it.id == payeeId } ?: return state
        val form = state.form
        val shouldFillCategory = !form.isEditing &&
            state.prefs.isRememberCategory &&
            !form.isSplitMode &&
            form.categoryId == TransactionIds.NO_CATEGORY_ID &&
            payee.lastCategoryId > 0L &&
            state.catalog.categories.any { it.id == payee.lastCategoryId }
        val nextCategoryId = if (shouldFillCategory) payee.lastCategoryId else form.categoryId
        val nextMode = if (shouldFillCategory) {
            val cat = state.catalog.categories.firstOrNull { it.id == nextCategoryId }
            if (cat != null && form.mode != TransactionMode.TRANSFER) {
                if (cat.isIncome) TransactionMode.INCOME else TransactionMode.EXPENSE
            } else {
                form.mode
            }
        } else {
            form.mode
        }
        return state.copy(
            form = form.copy(
                payeeId = payeeId,
                pendingPayeeName = null,
                payeeQuery = payee.title,
                categoryId = nextCategoryId,
                mode = nextMode,
            ),
            expandedField = null,
        )
    }
}
