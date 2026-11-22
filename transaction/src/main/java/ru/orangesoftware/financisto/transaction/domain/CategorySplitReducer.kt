// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import kotlin.math.abs

internal object CategorySplitReducer {
    private const val DEFAULT_DECIMALS = 2

    fun reduce(
        state: TransactionUiState.Ready,
        intent: TransactionIntent,
    ): TransactionUiState.Ready {
        return when (intent) {
            is TransactionIntent.SelectCategory -> selectCategory(state, intent.categoryId, intent.forSplitLocalId)
            is TransactionIntent.ClearCategory -> state.copy(
                form = state.form.copy(categoryId = TransactionIds.NO_CATEGORY_ID, isSplitMode = false)
            )
            is TransactionIntent.ToggleSplitMode -> toggleSplitMode(state)
            is TransactionIntent.OpenNewSplitEditor -> openNewSplit(state)
            is TransactionIntent.OpenEditSplitEditor -> openEditSplit(state, intent.localId)
            is TransactionIntent.SaveSplitItem -> saveSplitItem(state, intent.split)
            is TransactionIntent.RemoveSplitItem -> state.copy(
                form = state.form.copy(splits = state.form.splits.filterNot { it.localId == intent.localId }),
                validationErrors = emptyList(),
            )
            is TransactionIntent.RebalanceAdjustTotal -> rebalanceAdjustTotal(state)
            is TransactionIntent.RebalanceDistributeEvenly -> rebalanceEvenly(state)
            is TransactionIntent.RebalanceAdjustLast -> rebalanceLast(state)
            is TransactionIntent.CollapseSingleSplit -> collapseSingleSplit(state)
            else -> state
        }
    }

    private fun openNewSplit(state: TransactionUiState.Ready): TransactionUiState.Ready {
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
            activeSheet = ActiveSheet.SplitItemEditor(draftSplit, isNew = true),
        )
    }

    private fun openEditSplit(state: TransactionUiState.Ready, localId: Long): TransactionUiState.Ready {
        val target = state.form.splits.firstOrNull { it.localId == localId } ?: return state
        return state.copy(activeSheet = ActiveSheet.SplitItemEditor(target, isNew = false))
    }

    private fun selectCategory(
        state: TransactionUiState.Ready,
        categoryId: Long,
        forSplitLocalId: Long?,
    ): TransactionUiState.Ready {
        if (forSplitLocalId != null) {
            return selectSplitCategory(state, categoryId, forSplitLocalId)
        }
        val cat = state.catalog.categories.firstOrNull { it.id == categoryId }
        val form = state.form
        val nextMode = if (cat != null && form.mode != TransactionMode.TRANSFER) {
            if (cat.isIncome) TransactionMode.INCOME else TransactionMode.EXPENSE
        } else {
            form.mode
        }
        val fillLoc = form.isFromNewBlankLaunch && state.prefs.isRememberLocation &&
            form.locationId == TransactionIds.CURRENT_LOCATION_ID && (cat?.lastLocationId ?: 0L) > 0L
        val fillProj = form.isFromNewBlankLaunch && state.prefs.isRememberProject &&
            form.projectId == TransactionIds.NO_PROJECT_ID && (cat?.lastProjectId ?: 0L) > 0L
        val locTitle = if (fillLoc && cat != null) {
            state.catalog.locations.firstOrNull { it.id == cat.lastLocationId }?.title.orEmpty()
        } else {
            form.locationQuery
        }
        return state.copy(
            form = form.copy(
                categoryId = categoryId,
                isSplitMode = false,
                mode = nextMode,
                locationId = if (fillLoc && cat != null) cat.lastLocationId else form.locationId,
                locationQuery = locTitle,
                projectId = if (fillProj && cat != null) cat.lastProjectId else form.projectId,
            ),
            expandedField = null,
            activeSheet = null,
            validationErrors = emptyList(),
        )
    }

    private fun selectSplitCategory(
        state: TransactionUiState.Ready,
        categoryId: Long,
        forSplitLocalId: Long,
    ): TransactionUiState.Ready {
        val activeSheet = state.activeSheet
        if (activeSheet is ActiveSheet.SplitItemEditor && activeSheet.split.localId == forSplitLocalId) {
            val cat = state.catalog.categories.firstOrNull { it.id == categoryId }
            val updated = activeSheet.split.copy(
                categoryId = categoryId,
                isIncome = cat?.isIncome ?: activeSheet.split.isIncome,
            )
            return state.copy(activeSheet = activeSheet.copy(split = updated))
        }
        val updatedSplits = state.form.splits.map {
            if (it.localId == forSplitLocalId) it.copy(categoryId = categoryId) else it
        }
        return state.copy(form = state.form.copy(splits = updatedSplits), activeSheet = null)
    }

    private fun toggleSplitMode(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val form = state.form
        val enabling = !form.isSplitMode
        val shouldSeed = enabling && form.splits.isEmpty() && form.primaryAmountMinor > 0L
        val nextSplits = if (shouldSeed) {
            listOf(
                EditableSplit(
                    localId = form.nextSplitLocalId,
                    categoryId = form.categoryId.coerceAtLeast(TransactionIds.NO_CATEGORY_ID),
                    amountMinor = form.primaryAmountMinor,
                    isIncome = form.mode == TransactionMode.INCOME,
                    projectId = form.projectId,
                )
            )
        } else {
            form.splits
        }
        return state.copy(
            form = form.copy(
                isSplitMode = enabling,
                categoryId = if (enabling) TransactionIds.SPLIT_CATEGORY_ID else form.categoryId,
                splits = nextSplits,
                nextSplitLocalId = if (shouldSeed) form.nextSplitLocalId - 1L else form.nextSplitLocalId,
            ),
            expandedField = null,
            validationErrors = emptyList(),
        )
    }

    private fun saveSplitItem(state: TransactionUiState.Ready, item: EditableSplit): TransactionUiState.Ready {
        val exists = state.form.splits.any { it.localId == item.localId }
        val updated = if (exists) {
            state.form.splits.map { if (it.localId == item.localId) item else it }
        } else {
            state.form.splits + item
        }
        return state.copy(
            form = state.form.copy(splits = updated, isSplitMode = true),
            activeSheet = null,
            validationErrors = emptyList(),
        )
    }

    private fun rebalanceAdjustTotal(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val newTotal = SplitAllocationCalculator.adjustTotalMagnitude(state.form.splits)
        val (pCur, sCur) = TransactionValidator.resolveCurrencyMeta(state.form, state.catalog)
        val pDec = pCur?.decimals ?: DEFAULT_DECIMALS
        val sDec = sCur?.decimals ?: DEFAULT_DECIMALS
        val text = AmountScale.formatEditableAmount(newTotal, pDec)
        return AmountModeReducer.applyPrimaryAmountText(state, text, pDec, sDec)
    }

    private fun rebalanceEvenly(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val parentSigned = SplitAllocationCalculator.parentSignedMinor(state.form.primaryAmountMinor, state.form.mode)
        val delta = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, state.form.splits)
        return state.copy(
            form = state.form.copy(splits = SplitAllocationCalculator.distributeEvenly(state.form.splits, delta)),
            validationErrors = emptyList(),
        )
    }

    private fun rebalanceLast(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val parentSigned = SplitAllocationCalculator.parentSignedMinor(state.form.primaryAmountMinor, state.form.mode)
        val delta = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, state.form.splits)
        return state.copy(
            form = state.form.copy(splits = SplitAllocationCalculator.adjustLast(state.form.splits, delta)),
            validationErrors = emptyList(),
        )
    }

    private fun collapseSingleSplit(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val single = state.form.splits.singleOrNull() ?: return state
        return state.copy(
            form = state.form.copy(
                isSplitMode = false,
                categoryId = single.categoryId,
                projectId = single.projectId,
                splits = emptyList(),
            ),
            validationErrors = emptyList(),
        )
    }
}
