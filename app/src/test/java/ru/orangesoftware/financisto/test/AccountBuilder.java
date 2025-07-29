package ru.orangesoftware.financisto.test;

import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Currency;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 3/2/11 9:07 PM
 */
public class AccountBuilder {

    private final DatabaseAdapter db;
    private final Account account = new Account();

    public static Account createDefault(DatabaseAdapter db) {
        Currency c = CurrencyBuilder.createDefault(db);
        return createDefault(db, c);
    }

    public static Account createDefault(DatabaseAdapter db, Currency c) {
        return withDb(db).title("Cash").currency(c).create();
    }

    public static AccountBuilder withDb(DatabaseAdapter db) {
        return new AccountBuilder(db);
    }

    public AccountBuilder id(long v) {
        account.setId(v);
        return this;
    }

    private AccountBuilder(DatabaseAdapter db) {
        this.db = db;
    }

    public AccountBuilder title(String title) {
        account.setTitle(title);
        return this;
    }

    public AccountBuilder currency(Currency currency) {
        account.setCurrency(currency);
        return this;
    }

    public AccountBuilder number(String n) {
        account.setNumber(n);
        return this;
    }

    public AccountBuilder issuer(String v) {
        account.setIssuer(v);
        return this;
    }
    
    public AccountBuilder total(long amount) {
        account.setTotalAmount(amount);
        return this;
    }

    public AccountBuilder doNotIncludeIntoTotals() {
        account.setIncludeIntoTotals(false);
        return this;
    }

    public AccountBuilder inactive() {
        account.setActive(false);
        return this;
    }

    public Account create() {
        db.saveAccount(account);
        return account;
    }
}
