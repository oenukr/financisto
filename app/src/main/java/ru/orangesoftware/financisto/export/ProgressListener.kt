// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

fun interface ProgressListener {
    fun onProgress(percentage: Int)
}
