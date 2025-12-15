package ru.orangesoftware.financisto.export.qif

import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.utils.Logger
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val logger: Logger = DependenciesHolder().logger

private val DATE_DELIMITER_PATTERN = "/|'|\\.|-".toRegex()
private val MONEY_PREFIX_PATTERN = "\\D".toRegex()
private val HUNDRED = BigDecimal(100)

object QifUtils {
    @JvmStatic
    fun trimFirstChar(s: String): String =
        if (s.length > 1) s.substring(1) else ""

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
    @JvmStatic
    fun parseDate(sDate: String, format: QifDateFormat): Date {
        val cal = Calendar.getInstance()
        var month = cal.get(Calendar.MONTH)
        var day = cal.get(Calendar.DAY_OF_MONTH)
        var year = cal.get(Calendar.YEAR)

        val chunks = DATE_DELIMITER_PATTERN.split(sDate)

        when (format) {
            QifDateFormat.US_FORMAT -> {
                try {
                    month = chunks[0].trim().toInt()
                    day = chunks[1].trim().toInt()
                    year = chunks[2].trim().toInt()
                } catch (e: NumberFormatException) {
                    //eat it
                    logger.e(e, "Unable to parse US date")
                }
            }

            QifDateFormat.EU_FORMAT -> {
                try {
                    day = chunks[0].trim().toInt()
                    month = chunks[1].trim().toInt()
                    year = chunks[2].trim().toInt()
                } catch (e: NumberFormatException) {
                    logger.e(e, "Unable to parse EU date")
                }
            }

            else -> {
                logger.e("Invalid date format specified")
                return Date()
            }
        }

        if (year < 100) {
            year += if (year < 29) {
                2000
            } else {
                1900
            }
        }
        cal.set(year, month - 1, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    /**
     * Adopted from https://jgnash.svn.sourceforge.net/viewvc/jgnash/jgnash2/trunk/src/jgnash/imports/qif/QifUtils.java
     */
    @JvmStatic
    @JvmOverloads
    fun parseMoney(money: String?, locale: Locale = Locale.getDefault()): Long =
        parseDecimal(money?.trim().orEmpty())
            .recoverCatching { parseWithNumberFormat(money?.trim().orEmpty(), locale) }
            .map(::moneyAsLong).getOrElse { 0L }

    private fun parseDecimal(moneyStr: String): Result<BigDecimal> =
        runCatching { BigDecimal(moneyStr) }
            .recoverCatching {
                // Handle cases with currency symbols or grouping separators
                val split = MONEY_PREFIX_PATTERN.split(moneyStr)
                if (split.size > 1) {
                    val numberPart = StringBuilder()
                    if (moneyStr.startsWith("-")) {
                        numberPart.append('-')
                    }
                    // Re-assemble the number, assuming the last part is the fraction
                    for (i in 0 until split.lastIndex) {
                        numberPart.append(split[i])
                    }
                    numberPart.append('.').append(split.last())
                    BigDecimal(numberPart.toString())
                } else {
                    // If splitting doesn't help, rethrow to trigger the next fallback
                    throw it
                }
            }

    private fun parseWithNumberFormat(moneyStr: String, locale: Locale): BigDecimal =
        runCatching {
            val formatter = NumberFormat.getNumberInstance(locale)
            val parsedNumber = formatter.parse(moneyStr)
            // Using Float can introduce precision loss, but it's a fallback.
            var bd = BigDecimal.valueOf(parsedNumber?.toDouble() ?: 0.0)
            // Limit scale to avoid overly long fractional parts from parsing.
            if (bd.scale() > 6) {
                bd = bd.setScale(2, RoundingMode.HALF_UP)
            }
            bd
        }.onFailure {
            logger.e(it, "Could not parse money '$moneyStr' with locale '$locale'")
        }.getOrThrow()


    private fun moneyAsLong(bd: BigDecimal) = bd
        .multiply(HUNDRED)
        .toLong()

    @JvmStatic
    fun isTransferCategory(category: String?): Boolean =
        !category.isNullOrEmpty() && category.startsWith("[") && category.endsWith("]")
}
