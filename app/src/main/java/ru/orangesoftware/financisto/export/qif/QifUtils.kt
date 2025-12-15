package ru.orangesoftware.financisto.export.qif

import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.utils.Logger
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Date
import java.util.Locale

private val logger: Logger = DependenciesHolder().logger

private val DATE_DELIMITER_PATTERN = "/|'|\\.|-".toRegex()
private val MONEY_PREFIX_PATTERN = "\\D".toRegex()
private val HUNDRED = BigDecimal(100)


private fun parseDecimal(moneyStr: String): Result<BigDecimal> =
    runCatching { BigDecimal(moneyStr) }
        .recoverCatching {
            // Handle cases with currency symbols or grouping separators
            val split = MONEY_PREFIX_PATTERN.split(moneyStr)
            if (split.size > 2) {
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
        if (formatter is java.text.DecimalFormat) {
            formatter.isParseBigDecimal = true
        }
        var bd = when (val parsedNumber = formatter.parse(moneyStr)) {
            is BigDecimal -> parsedNumber
            null -> BigDecimal.ZERO
            else -> BigDecimal(parsedNumber.toString()) // Fallback for other Number types
        }
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
        val chunks = sDate
            .split(DATE_DELIMITER_PATTERN)
            .map(String::trim)
            .toMutableList()

        if (chunks.size != 3) {
            logger.e("Invalid date format: expected 3 parts but found ${chunks.size} in '$sDate'")
            return Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())
        }

        val pattern = when (format) {
            QifDateFormat.US_FORMAT -> "M/d/y"
            QifDateFormat.EU_FORMAT -> "d/M/y"
        }

        // Handles two-digit years by setting a pivot year.
        // Years from 40 to 99 are interpreted as 1940-1999.
        // Years from 00 to 39 are interpreted as 2000-2039.
        val formatter = DateTimeFormatterBuilder()
            .appendPattern(pattern)
            .parseDefaulting(
                ChronoField.YEAR_OF_ERA,
                Year.now().value.toLong(),
            ) // Default year if not present
            .toFormatter()

        val date = runCatching {
            chunks[2] = chunks[2].toInt().let {
                when {
                    it < 40 -> 2000 + it
                    it < 100 -> 1900 + it
                    else -> it
                }.toString()
            }

            // The input string is normalized by joining with a standard delimiter
            // to match the pattern expected by the formatter.
            LocalDate.parse(chunks.joinToString("/"), formatter)
        }.onFailure {
            logger.e(it, "Unable to parse date: '$sDate' with format $format")
        }.getOrElse { LocalDate.now() }

        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    /**
     * Adopted from https://jgnash.svn.sourceforge.net/viewvc/jgnash/jgnash2/trunk/src/jgnash/imports/qif/QifUtils.java
     */
    @JvmStatic
    @JvmOverloads
    fun parseMoney(money: String?, locale: Locale = Locale.getDefault()): Long =
        money?.trim().orEmpty().let{ moneyTrimmed ->
            parseDecimal(moneyTrimmed)
                .recoverCatching { parseWithNumberFormat(moneyTrimmed, locale) }
                .map(::moneyAsLong).getOrElse { 0L }
        }

    @JvmStatic
    fun isTransferCategory(category: String?): Boolean =
        !category.isNullOrEmpty() && category.startsWith("[") && category.endsWith("]")
}
