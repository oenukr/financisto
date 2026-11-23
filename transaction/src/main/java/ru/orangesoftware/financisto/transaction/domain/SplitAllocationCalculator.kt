// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.domain

import ru.orangesoftware.financisto.transaction.model.EditableSplit
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import java.math.BigInteger
import kotlin.math.abs

object SplitAllocationCalculator {
    private val MIN_LONG_BIG = BigInteger.valueOf(Long.MIN_VALUE)
    private val MAX_LONG_BIG = BigInteger.valueOf(Long.MAX_VALUE)

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
    ): List<Long>? {
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
    ): List<Long>? {
        val denom = BigInteger.valueOf(originalTotalSignedMinor).abs()
        val numerScale = BigInteger.valueOf(accountTotalSignedMinor).abs()
        val allocations = Array(splits.size) { BigInteger.ZERO }
        val remainders = Array(splits.size) { BigInteger.ZERO }
        var baseSum = BigInteger.ZERO

        for (index in splits.indices) {
            val product = BigInteger.valueOf(splitSignedMinor(splits[index])).multiply(numerScale)
            val divRem = product.divideAndRemainder(denom)
            val quotient = divRem[0]
            if (quotient < MIN_LONG_BIG || quotient > MAX_LONG_BIG) return null
            allocations[index] = quotient
            remainders[index] = divRem[1]
            baseSum = baseSum.add(quotient)
        }

        val deficit = BigInteger.valueOf(accountTotalSignedMinor).subtract(baseSum)
        if (deficit != BigInteger.ZERO) {
            distributeRoundingDeficit(allocations, remainders, deficit)
        }
        val withinRange = allocations.all { it >= MIN_LONG_BIG && it <= MAX_LONG_BIG }
        return if (withinRange) allocations.map(BigInteger::longValueExact) else null
    }

    private fun distributeRoundingDeficit(
        allocations: Array<BigInteger>,
        remainders: Array<BigInteger>,
        deficit: BigInteger,
    ) {
        val stepSign = deficit.signum()
        val step = BigInteger.valueOf(stepSign.toLong())
        val eligibleIndices = remainders.indices
            .filter { index ->
                if (stepSign > 0) remainders[index] > BigInteger.ZERO else remainders[index] < BigInteger.ZERO
            }
            .sortedWith(
                compareBy<Int> { index ->
                    if (stepSign > 0) remainders[index].negate() else remainders[index]
                }.thenBy { it }
            )

        var remaining = deficit.abs()
        for (index in eligibleIndices) {
            if (remaining <= BigInteger.ZERO) break
            allocations[index] = allocations[index].add(step)
            remaining = remaining.subtract(BigInteger.ONE)
        }
        if (remaining > BigInteger.ZERO) {
            val last = allocations.lastIndex
            allocations[last] = allocations[last].add(step.multiply(remaining))
        }
    }
}
