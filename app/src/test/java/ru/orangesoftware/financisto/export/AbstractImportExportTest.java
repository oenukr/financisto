package ru.orangesoftware.financisto.export;

import static org.junit.Assert.assertNotNull;

import ru.orangesoftware.financisto.db.AbstractDbTest;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.test.CurrencyBuilder;

public abstract class AbstractImportExportTest extends AbstractDbTest {

    protected Account createFirstAccount() {
        Currency c = createCurrency("SGD");
        Account account = new Account();
        account.setTitle("My Cash Account");
        account.setType(AccountType.CASH.name());
        account.setCurrency(c);
        account.setTotalAmount(0);
        account.setSortOrder(100);
        account.setNote("AAA\nBBB:CCC");
        db.saveAccount(account);
        assertNotNull(db.load(Account.class, account.getId()));
        return account;
    }

    protected Account createSecondAccount() {
        Currency currency = createCurrency("CZK");
        Account account = new Account();
        account.setTitle("My Bank Account");
        account.setType(AccountType.BANK.name());
        account.setCurrency(currency);
        account.setTotalAmount(0);
        account.setSortOrder(50);
        db.saveAccount(account);
        assertNotNull(db.load(Account.class, account.getId()));
        return account;
    }

    private Currency createCurrency(String currency) {
        Currency c = CurrencyBuilder.withDb(db)
                .title("Singapore Dollar")
                .name(currency)
                .separators("''", "'.'")
                .symbol("S$")
                .create();
        assertNotNull(db.load(Currency.class, c.getId()));
        return c;
    }

}
