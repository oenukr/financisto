package ru.orangesoftware.financisto.export

class ImportExportException @JvmOverloads constructor(
    val errorResId: Int,
    override val cause: Exception? = null,
    vararg val formatArgs: Any?
) : Exception()
