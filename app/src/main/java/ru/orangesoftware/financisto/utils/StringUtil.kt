package ru.orangesoftware.financisto.utils

import java.util.Locale

object StringUtil {

    fun capitalize(str: String?): String? = if (str.isNullOrEmpty()) {
        str
    } else {
        str.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    fun isEmpty(str: String?): Boolean = str.isNullOrBlank()

    fun emptyIfNull(str: CharSequence?): String = str?.toString() ?: ""

    fun generateQueryPlaceholders(num: Int): String = generateSeparated("?", ",", num)

    
    fun generateSeparated(value: String, delim: String, num: Int): String {
        val res: StringBuilder = StringBuilder(value)
        if (num <= 1) return res.toString()
        if (num == 2) return res.append(delim).append(value).toString()
        if (num == 3) return res.append(delim).append(value).append(delim).append(value).toString()

        for (i in 1 until num) {
            res.append(delim).append(value)
        }
        return res.toString()
    }
    
    /**
     * Fast replacement w/o using regexps
     * from <a href=https://stackoverflow.com/a/12026782/365675>here</a>
     */
    fun replaceAllIgnoreCase(source: String?, target: String?, replacement: String): String? {
        if (source == null || target.isNullOrEmpty() || target.length > source.length) {
            return source
        }

        return source.replace(target, replacement, true)
    }
}
