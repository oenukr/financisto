package ru.orangesoftware.financisto.export.qif

import java.io.BufferedWriter
import java.io.IOException

class QifBufferedWriter(private val bw: BufferedWriter) {
    @Throws(IOException::class)
    fun write(str: String): QifBufferedWriter {
        bw.write(str)
        return this
    }

    @Throws(IOException::class)
    fun newLine() = bw.write("\n")

    @Throws(IOException::class)
    fun end() = bw.write("^\n")

    @Throws(IOException::class)
    fun writeAccountsHeader() {
        bw.write("!Account")
        newLine()
    }

    @Throws(IOException::class)
    fun writeCategoriesHeader() {
        bw.write("!Type:Cat")
        newLine()
    }
}
