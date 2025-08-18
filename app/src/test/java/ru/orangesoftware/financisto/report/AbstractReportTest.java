package ru.orangesoftware.financisto.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import ru.orangesoftware.financisto.db.AbstractDbTest;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.graph.GraphUnit;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.test.AccountBuilder;
import ru.orangesoftware.financisto.test.CategoryBuilder;
import ru.orangesoftware.financisto.test.CurrencyBuilder;
import ru.orangesoftware.financisto.utils.CurrencyCache;

public abstract class AbstractReportTest extends AbstractDbTest {

    Currency c1;
    Currency c2;
    Account a1;
    Account a2;
    Account a3;
    Report report;
    Map<String, Category> categories;
    WhereFilter filter = WhereFilter.empty();
    CurrencyCache currencyCache;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        c1 = CurrencyBuilder.withDb(db).name("USD").title("Dollar").symbol("$").makeDefault().create();
        c2 = CurrencyBuilder.withDb(db).name("SGD").title("Singapore Dollar").symbol("S$").create();
        a1 = AccountBuilder.createDefault(db, c1);
        a2 = AccountBuilder.createDefault(db, c1);
        a3 = AccountBuilder.createDefault(db, c2);
        categories = CategoryBuilder.createDefaultHierarchy(db);
        report = createReport(false);
        currencyCache = new CurrencyCache(db.currencyDao());
    }

    protected abstract Report createReport(boolean includeTransfers);

    List<GraphUnit> assertReportReturnsData() {
        return assertReportReturnsData(IncomeExpense.BOTH);
    }

    List<GraphUnit> assertReportReturnsData(IncomeExpense incomeExpense) {
        report.setIncomeExpense(incomeExpense);
        ReportData data = report.getReport(db, filter);
        assertNotNull(data);
        List<GraphUnit> units = data.getUnits();
        assertNotNull(units);
        return units;
    }

    void assertName(GraphUnit unit, String name) {
        assertEquals(name, unit.name);
    }

    void assertIncome(GraphUnit u, long amount) {
        assertEquals(amount, u.getIncomeExpense().getIncome().longValue());
    }

    void assertExpense(GraphUnit u, long amount) {
        assertEquals(amount, u.getIncomeExpense().getExpense().longValue());
    }

}
