// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

fun interface ImportExportAsyncTaskListener {
	fun onCompleted(result: Any)
}
