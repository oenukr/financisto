// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionIds
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
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
            is TransactionIntent.OpenNewSplitEditor -> SplitEditorSheetReducer.openNewSplit(state)
            is TransactionIntent.OpenEditSplitEditor -> SplitEditorSheetReducer.openEditSplit(state, intent.localId)
            is TransactionIntent.UpdateSplitDraft -> SplitEditorSheetReducer.updateSplitDraft(state, intent.split)
            is TransactionIntent.SaveSplitItem -> SplitEditorSheetReducer.saveSplitItem(state, intent.split)
            is TransactionIntent.RemoveSplitItem -> state.copy(
                form = state.form.copy(splits = state.form.splits.filterNot { it.localId == intent.localId }),
                validationErrors = emptyList(),
            )
            is TransactionIntent.RebalanceAdjustTotal -> rebalanceAdjustTotal(state)
            is TransactionIntent.RebalanceDistributeEvenly -> rebalanceSplits(state, distributeEvenly = true)
            is TransactionIntent.RebalanceAdjustLast -> rebalanceSplits(state, distributeEvenly = false)
            is TransactionIntent.CollapseSingleSplit -> collapseSingleSplit(state)
            else -> state
        }
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
        val updatedForm = applySelectedCategoryToForm(state, categoryId, cat)
        return state.copy(
            form = updatedForm,
            expandedField = null,
            sheetStack = emptyList(),
            validationErrors = emptyList(),
        )
    }

    private fun applySelectedCategoryToForm(
        state: TransactionUiState.Ready,
        categoryId: Long,
        cat: CategoryOption?,
    ): TransactionFormState {
        val form = state.form
        val nextMode = if (cat != null && form.mode != TransactionMode.TRANSFER) {
            if (cat.isIncome) TransactionMode.INCOME else TransactionMode.EXPENSE
        } else {
            form.mode
        }
        val rememberedLocationId = resolveRememberedLocationId(state, cat)
        val locTitle = if (rememberedLocationId != form.locationId) {
            state.catalog.locations.firstOrNull { it.id == rememberedLocationId }?.title.orEmpty()
        } else {
            form.locationQuery
        }
        val canRememberProj = form.isFromNewBlankLaunch && state.prefs.isRememberProject
        val fillProj = canRememberProj &&
            form.projectId == TransactionIds.NO_PROJECT_ID &&
            (cat?.lastProjectId ?: 0L) > 0L
        return form.copy(
            categoryId = categoryId,
            isSplitMode = false,
            mode = nextMode,
            locationId = rememberedLocationId,
            locationQuery = locTitle,
            projectId = if (fillProj && cat != null) cat.lastProjectId else form.projectId,
        )
    }

    private fun resolveRememberedLocationId(
        state: TransactionUiState.Ready,
        cat: CategoryOption?,
    ): Long {
        val form = state.form
        val canRemember = form.isFromNewBlankLaunch && state.prefs.isRememberLocation
        val candidate = cat?.lastLocationId ?: 0L
        val shouldFill = canRemember &&
            form.locationId == TransactionIds.CURRENT_LOCATION_ID &&
            candidate > 0L
        return if (shouldFill) candidate else form.locationId
    }

    private fun selectSplitCategory(
        state: TransactionUiState.Ready,
        categoryId: Long,
        forSplitLocalId: Long,
    ): TransactionUiState.Ready {
        val cat = state.catalog.categories.firstOrNull { it.id == categoryId }
        val editor = state.activeSplitEditor?.takeIf { it.split.localId == forSplitLocalId }
        return if (editor != null) {
            val updatedSplit = editor.split.copy(
                categoryId = categoryId,
                isIncome = cat?.isIncome ?: editor.split.isIncome,
            )
            state.copy(sheetStack = listOf(editor.copy(split = updatedSplit)))
        } else {
            val updatedSplits = state.form.splits.map { split ->
                if (split.localId == forSplitLocalId) {
                    split.copy(
                        categoryId = categoryId,
                        isIncome = cat?.isIncome ?: split.isIncome,
                    )
                } else {
                    split
                }
            }
            state.copy(form = state.form.copy(splits = updatedSplits), sheetStack = emptyList())
        }
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
        val nextCategoryId = if (enabling) TransactionIds.SPLIT_CATEGORY_ID else TransactionIds.NO_CATEGORY_ID
        return state.copy(
            form = form.copy(
                isSplitMode = enabling,
                categoryId = nextCategoryId,
                splits = nextSplits,
                nextSplitLocalId = if (shouldSeed) form.nextSplitLocalId - 1L else form.nextSplitLocalId,
            ),
            expandedField = null,
            validationErrors = emptyList(),
        )
    }

    private fun rebalanceAdjustTotal(state: TransactionUiState.Ready): TransactionUiState.Ready {
        val allocatedSigned = SplitAllocationCalculator.allocatedSignedMinor(state.form.splits)
        val newTotal = abs(allocatedSigned)
        val nextMode = when {
            state.form.mode == TransactionMode.TRANSFER -> state.form.mode
            allocatedSigned > 0L -> TransactionMode.INCOME
            allocatedSigned < 0L -> TransactionMode.EXPENSE
            else -> state.form.mode
        }
        val stateWithMode = if (nextMode != state.form.mode) {
            state.copy(form = state.form.copy(mode = nextMode))
        } else {
            state
        }
        val pDec = stateWithMode.primaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val sDec = stateWithMode.secondaryCurrency?.decimals ?: DEFAULT_DECIMALS
        val text = AmountScale.formatEditableAmount(newTotal, pDec)
        return AmountModeReducer.applyPrimaryAmountText(stateWithMode, text, pDec, sDec)
    }

    private fun rebalanceSplits(
        state: TransactionUiState.Ready,
        distributeEvenly: Boolean,
    ): TransactionUiState.Ready {
        val parentSigned = SplitAllocationCalculator.parentSignedMinor(state.form.primaryAmountMinor, state.form.mode)
        val delta = SplitAllocationCalculator.unsplitSignedMinor(parentSigned, state.form.splits)
        val updatedSplits = if (distributeEvenly) {
            SplitAllocationCalculator.distributeEvenly(state.form.splits, delta)
        } else {
            SplitAllocationCalculator.adjustLast(state.form.splits, delta)
        }
        return state.copy(
            form = state.form.copy(splits = updatedSplits),
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
