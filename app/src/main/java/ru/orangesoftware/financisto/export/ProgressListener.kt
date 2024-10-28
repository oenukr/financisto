package ru.orangesoftware.financisto.export

fun interface ProgressListener {
    fun onProgress(percentage: Int)
}
