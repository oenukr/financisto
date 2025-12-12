/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.export.qif;

import static ru.orangesoftware.financisto.export.qif.QifDateFormat.EU_FORMAT;
import static ru.orangesoftware.financisto.export.qif.QifDateFormat.US_FORMAT;
import static ru.orangesoftware.financisto.utils.Utils.isNotEmpty;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.utils.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 10/12/11 11:40 PM
 */
public class QifUtils {

    private final static Logger logger = new DependenciesHolder().getLogger();

    private static final Pattern DATE_DELIMITER_PATTERN = Pattern.compile("/|'|\\.|-");
    private static final Pattern MONEY_PREFIX_PATTERN = Pattern.compile("\\D");
    private static final BigDecimal HUNDRED = new BigDecimal(100);

    public static String trimFirstChar(String s) {
        return s.length() > 1 ? s.substring(1) : "";
    }

    /**
     * Adopted from https://jgnash.svn.sourceforge.net/viewvc/jgnash/jgnash2/trunk/src/jgnash/imports/qif/QifUtils.java
     *
     * Converts a string into a data object
     * <p>
     * <p/>
     * format "6/21' 1" -> 6/21/2001 format "6/21'01" -> 6/21/2001 format "9/18'2001 -> 9/18/2001 format "06/21/2001"
     * format "06/21/01" format "3.26.03" -> German version of quicken format "03-26-2005" -> MSMoney format format
     * "1.1.2005" -> kmymoney2 20.1.94 European dd/mm/yyyy has been confirmed
     * <p/>
     * 21/2/07 -> 02/21/2007 UK, Quicken 2007 D15/2/07
     *
     * @param sDate String QIF date to parse
     * @param format String identifier of format to parse
     * @return Returns parsed date and current date if an error occurs
     */
    public static Date parseDate(String sDate, QifDateFormat format) {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int year = cal.get(Calendar.YEAR);

        String[] chunks = DATE_DELIMITER_PATTERN.split(sDate);

        if (format == US_FORMAT) {
            try {
                month = Integer.parseInt(chunks[0].trim());
                day = Integer.parseInt(chunks[1].trim());
                year = Integer.parseInt(chunks[2].trim());
            } catch (Exception e) {
                //eat it
                logger.e(e, "Unable to parse US date");
            }
        } else if (format == EU_FORMAT) {
            try {
                day = Integer.parseInt(chunks[0].trim());
                month = Integer.parseInt(chunks[1].trim());
                year = Integer.parseInt(chunks[2].trim());
            } catch (Exception e) {
                logger.e(e, "Unable to parse EU date");
            }
        } else {
            logger.e("Invalid date format specified");
            return new Date();
        }

        if (year < 100) {
            if (year < 29) {
                year += 2000;
            } else {
                year += 1900;
            }
        }
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Adopted from https://jgnash.svn.sourceforge.net/viewvc/jgnash/jgnash2/trunk/src/jgnash/imports/qif/QifUtils.java
     */
    public static long parseMoney(@NonNull String money, @Nullable Locale locale) {  // Declares a static method that parses a monetary string and returns its value as a long (likely cents)
        Locale sLocale = locale != null ? locale : Locale.getDefault();
        String sMoney = money;  // Assigns the input string to a local variable (sMoney)

        if (sMoney != null) {  // Checks if the input string is not null
            BigDecimal bdMoney;  // Declares a BigDecimal variable to hold the parsed monetary value
            sMoney = sMoney.trim();  // Removes leading/trailing whitespace to ensure clean parsing
            try {
                bdMoney = new BigDecimal(sMoney);  // Attempts to parse the string directly as a BigDecimal
                return moneyAsLong(bdMoney);  // If successful, converts BigDecimal to long and returns it
            } catch (NumberFormatException e) {  // If direct parsing fails (e.g., due to commas or other non-numeric characters)
                /* there must be commas, etc in the number.  Need to look for them
                 * and remove them first, and then try BigDecimal again.  If that
                 * fails, then give up and use NumberFormat and scale it down
                 * */
                String[] split = MONEY_PREFIX_PATTERN.split(sMoney);  // Splits the string into parts using a regex pattern (likely to remove currency symbols)
                if (split.length > 2) {  // Checks if the split resulted in more than 2 parts (indicating extra non-numeric characters, like commas)
                    StringBuilder buf = new StringBuilder();  // Uses a StringBuilder to construct a cleaned number string
                    if (sMoney.startsWith("-")) {  // Preserves a negative sign if the original string had one
                        buf.append('-');
                    }
                    for (int i = 0; i < split.length - 1; i++) {  // Loops through all parts except the last one (likely the decimal part)
                        buf.append(split[i]);  // Appends the integer part(s) without any separators
                    }
                    buf.append('.');  // Explicitly adds a decimal point (assuming the last part was the fractional part)
                    buf.append(split[split.length - 1]);  // Appends the last part (likely just digits)
                    try {
                        bdMoney = new BigDecimal(buf.toString());  // Attempts to parse the reconstructed string as BigDecimal
                        return moneyAsLong(bdMoney);  // If successful, converts and returns it
                    } catch (final NumberFormatException e2) {  // If the second attempt fails (e.g., malformed decimal)
                        logger.e("Second parse attempt failed, falling back to rounding");  // Logs an error before trying a fallback method
                    }
                }
                NumberFormat formatter = NumberFormat.getNumberInstance(sLocale);  // Gets a locale-aware NumberFormat to handle decimal/comma separators
                try {
                    Number num = formatter.parse(sMoney);  // Parses the string (e.g., "1,234.56" → "1234.56") into a Number object (usually double)
                    BigDecimal bd = BigDecimal.valueOf(num.floatValue());  // Converts the Number to a float and then to BigDecimal
                    if (bd.scale() > 6) {  // Checks if the number has more than 6 decimal places (e.g., 1234.567890 → true)
                        bd = bd.setScale(2, RoundingMode.HALF_UP);  // Rounds the number to 2 decimal places using HALF_UP rounding mode
                    }
                    return moneyAsLong(bd);  // Converts the rounded BigDecimal to long and returns it
                } catch (ParseException ignored) {  // If NumberFormat.parse() fails, silently ignores the exception
                }
                logger.e("Could not parse money " + sMoney);  // Logs an error if all parsing attempts fail
            }
        }
        return 0;  // Returns 0 if the input string is null or parsing fails entirely
    }

    private static long moneyAsLong(BigDecimal bd) {
        return bd.multiply(HUNDRED).intValue();
    }

    public static boolean isTransferCategory(String category) {
        return isNotEmpty(category) && category.startsWith("[") && category.endsWith("]");
    }

}
