package ru.orangesoftware.financisto.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ru.orangesoftware.financisto.db.AbstractDbTest;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.test.AccountBuilder;
import ru.orangesoftware.financisto.test.TransactionBuilder;

public class IntegrityCheckRunningBalanceTest extends AbstractDbTest {

    Account a1;
    Account a2;
    IntegrityCheckRunningBalance integrity;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        a1 = AccountBuilder.createDefault(db);
        a2 = AccountBuilder.createDefault(db);
        integrity = new IntegrityCheckRunningBalance(db);
    }

    @Test
    public void should_detect_that_running_balance_is_broken() {
        TransactionBuilder.withDb(db).account(a1).amount(1000).create();
        TransactionBuilder.withDb(db).account(a1).amount(2000).create();
        TransactionBuilder.withDb(db).account(a2).amount(-100).create();
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        breakRunningBalanceForAccount(a1);
        assertEquals(IntegrityCheck.Level.ERROR, integrity.check(getContext()).getLevel());

        db.rebuildRunningBalanceForAccount(a1);
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());

        breakRunningBalance();
        assertEquals(IntegrityCheck.Level.ERROR, integrity.check(getContext()).getLevel());

        db.rebuildRunningBalances();
        assertEquals(IntegrityCheck.Level.OK, integrity.check(getContext()).getLevel());
    }

    private void breakRunningBalanceForAccount(Account a) {
        db.db().execSQL("delete from running_balance where account_id=?", new String[]{String.valueOf(a.getId())});
    }

    private void breakRunningBalance() {
        db.db().execSQL("delete from running_balance");
    }

}
