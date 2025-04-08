package ru.orangesoftware.financisto.utils

import java.util.Locale

object StringUtils {
    fun String.capitalize(): String = replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}

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
        return if (num <= 1) {
            res.toString()
        } else if (num == 2) {
            res.append(delim).append(value).toString()
        } else if (num == 3) {
            res.append(delim).append(value).append(delim).append(value).toString()
        } else {
            for (i in 1 until num) {
                res.append(delim).append(value)
            }
            res.toString()
        }
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
