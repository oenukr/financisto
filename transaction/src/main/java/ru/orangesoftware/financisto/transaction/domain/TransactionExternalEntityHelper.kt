// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import androidx.lifecycle.SavedStateHandle
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.mvi.ActiveSheet
import ru.orangesoftware.financisto.transaction.mvi.ExternalEntityKind
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState

internal data class PendingExternalCategoryTarget(
    val splitLocalId: Long?,
    val wasSplitMode: Boolean,
)

internal object TransactionExternalEntityHelper {
    private const val KEY_PENDING_EXT_CATEGORY_HAD_TARGET = "transaction_pending_ext_category_had_target"
    private const val KEY_PENDING_EXT_CATEGORY_SPLIT_ID = "transaction_pending_ext_category_split_id"
    private const val KEY_PENDING_EXT_CATEGORY_WAS_SPLIT_MODE = "transaction_pending_ext_category_was_split_mode"

    fun captureCategoryTarget(ready: TransactionUiState.Ready): PendingExternalCategoryTarget {
        val splitLocalId = (ready.activeSheet as? ActiveSheet.CategoryBrowser)?.forSplitLocalId
            ?: ready.activeSplitEditor?.split?.localId
        return PendingExternalCategoryTarget(
            splitLocalId = splitLocalId,
            wasSplitMode = ready.form.isSplitMode || splitLocalId != null,
        )
    }

    fun savePendingCategoryTarget(
        savedStateHandle: SavedStateHandle,
        target: PendingExternalCategoryTarget,
    ) {
        savedStateHandle[KEY_PENDING_EXT_CATEGORY_HAD_TARGET] = true
        savedStateHandle[KEY_PENDING_EXT_CATEGORY_WAS_SPLIT_MODE] = target.wasSplitMode
        if (target.splitLocalId != null) {
            savedStateHandle[KEY_PENDING_EXT_CATEGORY_SPLIT_ID] = target.splitLocalId
        } else {
            savedStateHandle.remove<Long>(KEY_PENDING_EXT_CATEGORY_SPLIT_ID)
        }
    }

    fun consumePendingCategoryTarget(savedStateHandle: SavedStateHandle): PendingExternalCategoryTarget? {
        val hasTarget = savedStateHandle.get<Boolean>(KEY_PENDING_EXT_CATEGORY_HAD_TARGET) == true
        if (!hasTarget) return null
        val target = PendingExternalCategoryTarget(
            splitLocalId = savedStateHandle.get<Long>(KEY_PENDING_EXT_CATEGORY_SPLIT_ID),
            wasSplitMode = savedStateHandle.get<Boolean>(KEY_PENDING_EXT_CATEGORY_WAS_SPLIT_MODE) == true,
        )
        clearPendingCategoryTarget(savedStateHandle)
        return target
    }

    fun clearPendingCategoryTarget(savedStateHandle: SavedStateHandle) {
        savedStateHandle.remove<Boolean>(KEY_PENDING_EXT_CATEGORY_HAD_TARGET)
        savedStateHandle.remove<Long>(KEY_PENDING_EXT_CATEGORY_SPLIT_ID)
        savedStateHandle.remove<Boolean>(KEY_PENDING_EXT_CATEGORY_WAS_SPLIT_MODE)
    }

    fun applyExternalEntityCreated(
        ready: TransactionUiState.Ready,
        refreshedCatalog: TransactionCatalog,
        kind: ExternalEntityKind,
        createdId: Long,
        pendingCategoryTarget: PendingExternalCategoryTarget? = null,
    ): TransactionUiState.Ready {
        val withCatalog = ready.copy(catalog = refreshedCatalog)
        return when (kind) {
            ExternalEntityKind.LOCATION ->
                TransactionReducer.reduce(withCatalog, TransactionIntent.SelectLocation(createdId))
            ExternalEntityKind.CATEGORY -> applyExternalCategoryCreated(
                ready = withCatalog,
                createdId = createdId,
                target = pendingCategoryTarget ?: captureCategoryTarget(ready),
            )
        }
    }

    private fun applyExternalCategoryCreated(
        ready: TransactionUiState.Ready,
        createdId: Long,
        target: PendingExternalCategoryTarget,
    ): TransactionUiState.Ready {
        val targetSplitId = target.splitLocalId
        return when {
            targetSplitId != null -> applyCategoryToExistingSplit(ready, createdId, targetSplitId)
            target.wasSplitMode || ready.form.isSplitMode -> ready
            else -> TransactionReducer.reduce(
                ready,
                TransactionIntent.SelectCategory(createdId, forSplitLocalId = null),
            )
        }
    }

    private fun applyCategoryToExistingSplit(
        ready: TransactionUiState.Ready,
        createdId: Long,
        splitLocalId: Long,
    ): TransactionUiState.Ready {
        val cat = ready.catalog.categories.firstOrNull { it.id == createdId }
        val editor = ready.activeSplitEditor?.takeIf { it.split.localId == splitLocalId }
        return when {
            editor != null -> {
                val updatedSplit = editor.split.copy(
                    categoryId = createdId,
                    isIncome = cat?.isIncome ?: editor.split.isIncome,
                )
                ready.copy(sheetStack = listOf(editor.copy(split = updatedSplit)))
            }
            ready.form.isSplitMode && ready.form.splits.any { it.localId == splitLocalId } -> {
                val updatedSplits = ready.form.splits.map { split ->
                    if (split.localId == splitLocalId) {
                        split.copy(
                            categoryId = createdId,
                            isIncome = cat?.isIncome ?: split.isIncome,
                        )
                    } else {
                        split
                    }
                }
                val nextStack = if (
                    (ready.activeSheet as? ActiveSheet.CategoryBrowser)?.forSplitLocalId == splitLocalId
                ) {
                    ready.sheetStack.dropLast(1)
                } else {
                    ready.sheetStack
                }
                ready.copy(form = ready.form.copy(splits = updatedSplits), sheetStack = nextStack)
            }
            else -> ready
        }
    }
}
