// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import kotlin.math.abs

internal object SplitEditorSheetReducer {

    fun openNewSplit(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val form = state.form
        val parentSigned = SplitAllocationCalculator.parentSignedMinor(form.primaryAmountMinor, form.mode)
        val unsplit = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, form.splits)
        val defaultIncome = if (unsplit != 0L) unsplit > 0L else form.mode == TransactionMode.INCOME
        val draftSplit = EditableSplit(
            localId = form.nextSplitLocalId,
            categoryId = TransactionIds.NO_CATEGORY_ID,
            amountMinor = abs(unsplit),
            isIncome = defaultIncome,
        )
        return state.copy(
            form = form.copy(nextSplitLocalId = form.nextSplitLocalId - 1L),
            sheetStack = listOf(ActiveSheet.SplitItemEditor(draftSplit, isNew = true)),
        )
    }

    fun openEditSplit(state: TransactionUiState.Ready, localId: Long): TransactionUiState.Ready {
        val target = state.form.splits.firstOrNull { it.localId == localId } ?: return state
        return state.copy(sheetStack = listOf(ActiveSheet.SplitItemEditor(target, isNew = false)))
    }

    fun updateSplitDraft(state: TransactionUiState.Ready, draft: EditableSplit): TransactionUiState.Ready {
        val updatedStack = state.sheetStack.map { sheet ->
            if (sheet is ActiveSheet.SplitItemEditor && sheet.split.localId == draft.localId) {
                sheet.copy(split = draft)
            } else {
                sheet
            }
        }
        return state.copy(sheetStack = updatedStack)
    }

    fun saveSplitItem(state: TransactionUiState.Ready, item: EditableSplit): TransactionUiState.Ready {
        val exists = state.form.splits.any { it.localId == item.localId }
        val updated = if (exists) {
            state.form.splits.map { if (it.localId == item.localId) item else it }
        } else {
            state.form.splits + item
        }
        return state.copy(
            form = state.form.copy(
                splits = updated,
                isSplitMode = true,
                categoryId = TransactionIds.SPLIT_CATEGORY_ID,
            ),
            sheetStack = emptyList(),
            validationErrors = emptyList(),
        )
    }
}
