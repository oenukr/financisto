package ru.orangesoftware.financisto.utils

import ru.orangesoftware.financisto.model.Transaction

object SplitAdjuster {

    @JvmStatic
    fun adjustEvenly(splits: List<Transaction>, unsplitAmount: Long) {
        if (noSplits(splits)) {
            return
        }
        val count = splits.size
        val amount = unsplitAmount / count
        splits.forEach { it.fromAmount += amount }
        val extra = unsplitAmount - amount * count
        if (extra != 0L) {
            val sign = if (extra > 0) 1 else -1
            for(i in count - 1 downTo count - sign * extra) {
                splits[i.toInt()].fromAmount += sign
            }
        }
    }

    @JvmStatic
    fun adjustLast(splits: List<Transaction>, unsplitAmount: Long) {
        if (noSplits(splits)) {
            return
        }
        adjustSplit(splits[splits.size - 1], unsplitAmount)
    }

    @JvmStatic
    fun adjustSplit(split: Transaction, unsplitAmount: Long) {
        split.fromAmount += unsplitAmount
    }

    @JvmStatic
    fun noSplits(splits: List<Transaction>?): Boolean = splits.isNullOrEmpty()
}
