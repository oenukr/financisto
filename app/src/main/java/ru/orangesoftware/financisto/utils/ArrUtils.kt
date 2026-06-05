package ru.orangesoftware.financisto.utils

object ArrUtils {
    @JvmStatic
    fun strListToArr(list: List<String>?): Array<String> {
        return list?.toTypedArray() ?: emptyArray()
    }

    @JvmStatic
    fun joinArrays(a1: Array<String>, a2: Array<String>): Array<String> {
        return a1 + a2
    }

    @JvmStatic
    fun <T> isEmpty(arr: Array<T>?): Boolean {
        return arr.isNullOrEmpty()
    }
}
