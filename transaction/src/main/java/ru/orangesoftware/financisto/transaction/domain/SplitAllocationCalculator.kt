// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import kotlin.math.abs
import kotlin.math.roundToLong

object SplitAllocationCalculator {

    fun parentSignedMinor(primaryMagnitudeMinor: Long, mode: TransactionMode): Long {
        return when (mode) {
            TransactionMode.INCOME -> abs(primaryMagnitudeMinor)
            TransactionMode.EXPENSE, TransactionMode.TRANSFER -> -abs(primaryMagnitudeMinor)
        }
    }

    fun splitSignedMinor(split: EditableSplit): Long {
        val magnitude = abs(split.amountMinor)
        return if (split.isIncome) magnitude else -magnitude
    }

    fun allocatedSignedMinor(splits: List<EditableSplit>): Long {
        return splits.sumOf(::splitSignedMinor)
    }

    fun unsplitSignedMinor(parentSignedMinor: Long, splits: List<EditableSplit>): Long {
        return parentSignedMinor - allocatedSignedMinor(splits)
    }

    fun adjustTotalMagnitude(splits: List<EditableSplit>): Long {
        return abs(allocatedSignedMinor(splits))
    }

    fun distributeEvenly(
        splits: List<EditableSplit>,
        unsplitSignedDelta: Long,
    ): List<EditableSplit> {
        if (splits.isEmpty() || unsplitSignedDelta == 0L) return splits
        val count = splits.size
        val perSplit = unsplitSignedDelta / count
        val remainder = unsplitSignedDelta - perSplit * count
        val remainderStep = if (remainder > 0L) 1L else -1L
        val remainderAbs = abs(remainder)

        return splits.mapIndexed { index, split ->
            val extra = if (count - 1 - index < remainderAbs) remainderStep else 0L
            val updatedSigned = splitSignedMinor(split) + perSplit + extra
            split.copy(
                amountMinor = abs(updatedSigned),
                isIncome = updatedSigned > 0L,
            )
        }
    }

    fun adjustLast(
        splits: List<EditableSplit>,
        unsplitSignedDelta: Long,
    ): List<EditableSplit> {
        if (splits.isEmpty() || unsplitSignedDelta == 0L) return splits
        val lastIndex = splits.lastIndex
        return splits.mapIndexed { index, split ->
            if (index != lastIndex) {
                split
            } else {
                val updatedSigned = splitSignedMinor(split) + unsplitSignedDelta
                split.copy(
                    amountMinor = abs(updatedSigned),
                    isIncome = updatedSigned > 0L,
                )
            }
        }
    }

    /**
     * Converts foreign-currency splits into signed account-currency minor units proportionally,
     * absorbing any rounding remainder into the last split so their sum equals [accountTotalSignedMinor].
     */
    fun allocateAccountAmounts(
        splits: List<EditableSplit>,
        originalTotalSignedMinor: Long,
        accountTotalSignedMinor: Long,
    ): List<Long> {
        return when {
            splits.isEmpty() -> emptyList()
            originalTotalSignedMinor == 0L -> List(splits.size) { index ->
                if (index == splits.lastIndex) accountTotalSignedMinor else 0L
            }
            else -> {
                val ratio = abs(accountTotalSignedMinor).toDouble() / abs(originalTotalSignedMinor).toDouble()
                var runningSum = 0L
                splits.mapIndexed { index, split ->
                    if (index == splits.lastIndex) {
                        accountTotalSignedMinor - runningSum
                    } else {
                        val converted = (splitSignedMinor(split) * ratio).roundToLong()
                        runningSum += converted
                        converted
                    }
                }
            }
        }
    }
}
