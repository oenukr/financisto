package ru.orangesoftware.financisto.export

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.orangesoftware.financisto.export.qif.QifDateFormat.EU_FORMAT
import ru.orangesoftware.financisto.export.qif.QifDateFormat.US_FORMAT
import ru.orangesoftware.financisto.export.qif.QifUtils.parseDate
import ru.orangesoftware.financisto.export.qif.QifUtils.parseMoney
import ru.orangesoftware.financisto.export.qif.QifUtils.trimFirstChar
import ru.orangesoftware.financisto.test.DateTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class QifUtilsTest {

    @Test
    fun `should trim first char`() {
        assertEquals("My Cash Account", trimFirstChar("NMy Cash Account"))
        assertEquals("-10.5", trimFirstChar("X-10.5"))
    }

    @Test
    fun `should parse dates`() {
        assertEquals(DateTime.date(2011, 2, 7).atMidnight().asDate(), parseDate("07/02/2011", EU_FORMAT))
        assertEquals(DateTime.date(2011, 2, 7).atMidnight().asDate(), parseDate("07/02/2011", EU_FORMAT))
        assertEquals(DateTime.date(2011, 2, 7).atMidnight().asDate(), parseDate("02/07/2011", US_FORMAT))
        assertEquals(DateTime.date(2011, 2, 7).atMidnight().asDate(), parseDate("07.02.11", EU_FORMAT))
        assertEquals(DateTime.date(2011, 2, 7).atMidnight().asDate(), parseDate("07.02'11", EU_FORMAT))
        assertEquals(DateTime.date(2011, 1, 23).atMidnight().asDate(), parseDate("1.23'11", US_FORMAT))
    }

    @Test
    fun `should parse money`() {
        assertEquals(100, parseMoney("1.0", Locale.US))
        assertEquals(-100, parseMoney("-1.", Locale.US))
        assertEquals(101, parseMoney("1,01", Locale.GERMANY))
        assertEquals(1234567, parseMoney("12,345.67", Locale.US))
        assertEquals(1234567, parseMoney("12.345,67", Locale.GERMANY))
        assertEquals(50, parseMoney("0.5", Locale.US))
        assertEquals(-75, parseMoney("-0,75", Locale.GERMANY))
        assertEquals(1000, parseMoney("10", Locale.US))
        assertEquals(1000, parseMoney("10,00", Locale.GERMANY))
        assertEquals(12345, parseMoney("123.45", Locale.US))
        assertEquals(12345, parseMoney("123,45", Locale.GERMANY))
        assertEquals(100250, parseMoney("1,002.5", Locale.US))
        assertEquals(100250, parseMoney("1.002,5", Locale.GERMANY))
        assertEquals(0, parseMoney("0", Locale.US))
        assertEquals(0, parseMoney("0,0", Locale.GERMANY))
    }
}
