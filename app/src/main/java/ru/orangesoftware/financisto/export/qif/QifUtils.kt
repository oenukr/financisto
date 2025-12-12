@file:JvmName("QifUtils")

package ru.orangesoftware.financisto.export.qif

import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.utils.Logger
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParseException
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

private val logger: Logger = DependenciesHolder().logger

private val DATE_DELIMITER_PATTERN = Pattern.compile("/|'|\\.|-")
private val MONEY_PREFIX_PATTERN = Pattern.compile("\\D")
private val HUNDRED = BigDecimal(100)

fun trimFirstChar(s: String): String {
    return if (s.length > 1) s.substring(1) else ""
}

/**
 * Adopted from https://jgnash.svn.sourceforge.net/viewvc/jgnash/jgnash2/trunk/src/jgnash/imports/qif/QifUtils.java
 *
 * Converts a string into a data object
 *
 * format "6/21' 1" -> 6/21/2001 format "6/21'01" -> 6/21/2001 format "9/18'2001 -> 9/18/2001 format "06/21/2001"
 * format "06/21/01" format "3.26.03" -> German version of quicken format "03-26-2005" -> MSMoney format format
 * "1.1.2005" -> kmymoney2 20.1.94 European dd/mm/yyyy has been confirmed
 *
 * 21/2/07 -> 02/21/2007 UK, Quicken 2007 D15/2/07
 *
 * @param sDate String QIF date to parse
 * @param format String identifier of format to parse
 * @return Returns parsed date and current date if an error occurs
 */
fun parseDate(sDate: String, format: QifDateFormat): Date {
    val cal = Calendar.getInstance()
    var month = cal.get(Calendar.MONTH)
    var day = cal.get(Calendar.DAY_OF_MONTH)
    var year = cal.get(Calendar.YEAR)

    val chunks = DATE_DELIMITER_PATTERN.split(sDate)

    if (format == QifDateFormat.US_FORMAT) {
        try {
            month = chunks[0].trim().toInt()
            day = chunks[1].trim().toInt()
            year = chunks[2].trim().toInt()
        } catch (e: Exception) {
            //eat it
            logger.e(e, "Unable to parse US date")
        }
    } else if (format == QifDateFormat.EU_FORMAT) {
        try {
            day = chunks[0].trim().toInt()
            month = chunks[1].trim().toInt()
            year = chunks[2].trim().toInt()
        } catch (e: Exception) {
            logger.e(e, "Unable to parse EU date")
        }
    } else {
        logger.e("Invalid date format specified")
        return Date()
    }

    if (year < 100) {
        if (year < 29) {
            year += 2000
        } else {
            year += 1900
        }
    }
    cal.set(year, month - 1, day, 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

/**
 * Adopted from https://jgnash.svn.sourceforge.net/viewvc/jgnash/jgnash2/trunk/src/jgnash/imports/qif/QifUtils.java
 */
@JvmOverloads
fun parseMoney(money: String?, locale: Locale = Locale.getDefault()): Long {
    val sMoney = money?.trim() // to be safe

    if (sMoney != null) {
        var bdMoney: BigDecimal
        try {
            bdMoney = BigDecimal(sMoney)
            return moneyAsLong(bdMoney)
        } catch (e: NumberFormatException) {
            /* there must be commas, etc in the number.  Need to look for them
             * and remove them first, and then try BigDecimal again.  If that
             * fails, then give up and use NumberFormat and scale it down
             * */
            val split = MONEY_PREFIX_PATTERN.split(sMoney)
            if (split.size > 2) {
                val buf = StringBuilder()
                if (sMoney.startsWith("-")) {
                    buf.append('-')
                }
                for (i in 0 until split.size - 1) {
                    buf.append(split[i])
                }
                buf.append('.')
                buf.append(split[split.size - 1])
                try {
                    bdMoney = BigDecimal(buf.toString())
                    return moneyAsLong(bdMoney)
                } catch (e2: NumberFormatException) {
                    logger.e("Second parse attempt failed, falling back to rounding")
                }
            }
            val formatter = NumberFormat.getNumberInstance(locale)
            try {
                val num = formatter.parse(sMoney)
                if (num != null) {
                    var bd = BigDecimal.valueOf(num.toFloat().toDouble())
                    if (bd.scale() > 6) {
                        bd = bd.setScale(2, RoundingMode.HALF_UP)
                    }
                    return moneyAsLong(bd)
                }
            } catch (ignored: ParseException) {
            }
            logger.e("Could not parse money $sMoney")
        }
    }
    return 0L
}

private fun moneyAsLong(bd: BigDecimal): Long {
    return bd.multiply(HUNDRED).toInt().toLong()
}

fun isTransferCategory(category: String?): Boolean {
    return !category.isNullOrEmpty() && category.startsWith("[") && category.endsWith("]")
}
