// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import java.math.BigInteger
import kotlin.math.abs

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
     * Converts foreign-currency splits into signed account-currency minor units proportionally
     * using exact largest-remainder allocation so their sum equals [accountTotalSignedMinor]
     * without reversing any split's direction relative to its original sign.
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
            else -> allocateProportionally(splits, originalTotalSignedMinor, accountTotalSignedMinor)
        }
    }

    private fun allocateProportionally(
        splits: List<EditableSplit>,
        originalTotalSignedMinor: Long,
        accountTotalSignedMinor: Long,
    ): List<Long> {
        val denom = BigInteger.valueOf(originalTotalSignedMinor).abs()
        val numerScale = BigInteger.valueOf(accountTotalSignedMinor).abs()
        val allocations = LongArray(splits.size)
        val remainders = Array(splits.size) { BigInteger.ZERO }
        var baseSum = 0L

        splits.forEachIndexed { index, split ->
            val product = BigInteger.valueOf(splitSignedMinor(split)).multiply(numerScale)
            val divRem = product.divideAndRemainder(denom)
            val base = divRem[0].toLong()
            allocations[index] = base
            remainders[index] = divRem[1]
            baseSum += base
        }

        val deficit = accountTotalSignedMinor - baseSum
        if (deficit != 0L) {
            distributeRoundingDeficit(allocations, remainders, deficit)
        }
        return allocations.toList()
    }

    private fun distributeRoundingDeficit(
        allocations: LongArray,
        remainders: Array<BigInteger>,
        deficit: Long,
    ) {
        val step = if (deficit > 0L) 1L else -1L
        val eligibleIndices = remainders.indices
            .filter { index ->
                if (step > 0L) remainders[index] > BigInteger.ZERO else remainders[index] < BigInteger.ZERO
            }
            .sortedWith(
                compareBy<Int> { index ->
                    if (step > 0L) remainders[index].negate() else remainders[index]
                }.thenBy { it }
            )

        var remaining = abs(deficit)
        for (index in eligibleIndices) {
            if (remaining <= 0L) break
            allocations[index] += step
            remaining--
        }
        if (remaining > 0L) {
            allocations[allocations.lastIndex] += step * remaining
        }
    }
}
