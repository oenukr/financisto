package ru.orangesoftware.financisto.db;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.test.AccountBuilder;

public class AccountTest extends AbstractDbTest {

    @Test
    public void duplication_and_sort_order_ignoring() {
        Account a1 = AccountBuilder.createDefault(db);
        long dup1Id = db.duplicate(Account.class, a1.getId());
        Account dup1 = db.getAccount(dup1Id);

        assertEquals(a1.getTitle(), dup1.getTitle());
        assertEquals(a1.getId() + 1, dup1.getId());
        Assert.assertFalse(db.updateEntitySortOrder(a1, 10));
    }

}
