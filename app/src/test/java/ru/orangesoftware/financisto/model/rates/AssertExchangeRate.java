// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.model.rates;

import static org.junit.Assert.assertEquals;

import ru.orangesoftware.financisto.db.AbstractDbTest;
import ru.orangesoftware.financisto.rates.ExchangeRate;
import ru.orangesoftware.financisto.test.DateTime;

public abstract class AssertExchangeRate extends AbstractDbTest {

    public static void assertRate(DateTime date, double rate, ExchangeRate r) {
        assertEquals(rate, r.rate, 0.00001d);
        assertEquals(date.atMidnight().asLong(), r.date);
    }

}
