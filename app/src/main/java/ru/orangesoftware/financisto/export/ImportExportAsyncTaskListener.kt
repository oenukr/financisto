package ru.orangesoftware.financisto.export

fun interface ImportExportAsyncTaskListener {
	fun onCompleted(result: Any)
}
