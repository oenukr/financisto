package ru.orangesoftware.financisto.export;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;

import ru.orangesoftware.financisto.export.qif.QifExport;
import ru.orangesoftware.financisto.export.qif.QifExportOptions;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.test.CategoryBuilder;
import ru.orangesoftware.financisto.test.DateTime;
import ru.orangesoftware.financisto.test.TransactionBuilder;

public class QifExportCurrencyTest extends AbstractExportTest<QifExport, QifExportOptions> {

    @Override
    protected QifExport createExport(QifExportOptions options) {
        return new QifExport(getContext(), db, options);
    }

    private String exportAsString() throws Exception {
        QifExportOptions options = new QifExportOptions(Currency.EMPTY, new SimpleDateFormat(QifExportOptions.DEFAULT_DATE_FORMAT), null, WhereFilter.empty(), false);
        return exportAsString(options);
    }

    @Test
    public void should_export_transfer_split_with_different_currencies() throws Exception {
        Account a1 = createFirstAccount(); // SGD, symbol S$
        Account a2 = createSecondAccount(); // CZK, symbol S$ (due to test helper quirk)

        // Transaction in a1 (SGD): -100.00 SGD total.
        // Split 1: Transfer to a2. -100.00 SGD -> +1000.00 CZK
        // fromAmount = -10000 (cents), toAmount = 100000 (cents)

        TransactionBuilder.withDb(db).account(a1).amount(-10000).dateTime(DateTime.date(2011, 7, 12))
                .category(CategoryBuilder.split(db))
                .withTransferSplit(a2, -10000, 100000)
                .create();

        String output = exportAsString();

        assertTrue(output.contains("NMy Cash Account"));
        assertTrue(output.contains("T-100.00"));
        assertTrue(output.contains("S[My Bank Account]"));
        assertTrue(output.contains("$-100.00"));
        // Expect memo with destination amount.
        // 1000.00 with S$ symbol.
        assertTrue(output.contains("E(1000.00 S$)"));

        assertTrue(output.contains("NMy Bank Account"));
        assertTrue(output.contains("T1000.00"));
        assertTrue(output.contains("L[My Cash Account]"));
        // This transaction comes from fromBlotterCursor logic.
        // The "toAccount" is A1.
        // The "toAmount" is the amount in A1, which is -100.00.
        // So we expect memo to show amount in A1: -100.00 S$.

        assertTrue(output.contains("M(-100.00 S$)"));
    }

    @Test
    public void should_export_transfer_split_with_different_currencies_and_existing_memo() throws Exception {
        Account a1 = createFirstAccount(); // SGD
        Account a2 = createSecondAccount(); // CZK

        TransactionBuilder.withDb(db).account(a1).amount(-10000).dateTime(DateTime.date(2011, 7, 12))
                .category(CategoryBuilder.split(db))
                .withTransferSplit(a2, -10000, 100000, "Existing Memo")
                .create();

        String output = exportAsString();

        // Expected memo: "Existing Memo (1000.00 S$)"
        assertTrue(output.contains("EExisting Memo (1000.00 S$)"));
    }
}
