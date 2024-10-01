package ru.orangesoftware.financisto.export.qif

import ru.orangesoftware.financisto.export.CategoryInfo
import ru.orangesoftware.financisto.export.qif.QifUtils.trimFirstChar
import ru.orangesoftware.financisto.model.Category
import java.io.IOException

class QifCategory @JvmOverloads constructor(
    name: String? = null,
    income: Boolean = false,
) : CategoryInfo(name, income) {
    companion object {
        @JvmStatic
        fun fromCategory(c: Category): QifCategory = QifCategory(
            buildName(c),
            c.isIncome,
        )
    }

    @Throws(IOException::class)
    fun writeTo(qifWriter: QifBufferedWriter) {
        qifWriter.write("N").write(name).newLine()
        qifWriter.write(if (isIncome) "I" else "E").newLine()
        qifWriter.end()
    }

    @Throws(IOException::class)
    fun readFrom(r: QifBufferedReader) {
        r.readLinesUntil { it.startsWith("^") }.forEach { line ->
            when {
                line.startsWith("N") -> this.name = trimFirstChar(line)
                line.startsWith("I") -> this.isIncome = true
            }
        }
    }

    private fun QifBufferedReader.readLinesUntil(predicate: (String) -> Boolean): Sequence<String> =
        generateSequence { readLine() }.takeWhile { !predicate(it) }
}
