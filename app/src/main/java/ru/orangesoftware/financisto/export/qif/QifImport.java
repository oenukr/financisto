/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.export.qif;

import static ru.orangesoftware.financisto.utils.Utils.isEmpty;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.backup.FullDatabaseImport;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.export.CategoryCache;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Payee;
import ru.orangesoftware.financisto.model.Project;
import ru.orangesoftware.financisto.model.Transaction;
import ru.orangesoftware.financisto.utils.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 9/25/11 9:54 PM
 */
public class QifImport extends FullDatabaseImport {

    private final Logger logger = new DependenciesHolder().getLogger();

    private final QifImportOptions options;

    private final Map<String, QifAccount> accountTitleToAccount = new HashMap<>();
    private final Map<String, Long> payeeToId = new HashMap<>();
    private final Map<String, Long> projectToId = new HashMap<>();
    private final CategoryCache categoryCache = new CategoryCache();

    public QifImport(Context context, DatabaseAdapter db, QifImportOptions options) {
        super(context, db);
        this.options = options;
    }

    @Override
    protected List<String> tablesToClean() {
        List<String> backupTables = super.tablesToClean();
        backupTables.remove("currency");
        backupTables.remove("currency_exchange_rate");
        return backupTables;
    }

    @Override
    protected void restoreDatabase() throws IOException {
        doImport();
    }

    public void doImport() throws IOException {
        long t0 = System.currentTimeMillis();
        DocumentFile file = DocumentFile.fromSingleUri(getContext(), Uri.parse(options.getFilename()));
        InputStream inputStream = getContext().getContentResolver().openInputStream(file.getUri());
        QifBufferedReader r = new QifBufferedReader(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
        QifParser parser = new QifParser(r, options.getDateFormat());
        parser.parse();
        long t1 = System.currentTimeMillis();
        logger.i("QIF Import: Parsing done in "+ TimeUnit.MILLISECONDS.toSeconds(t1-t0)+"s");
        doImport(parser);
        long t2 = System.currentTimeMillis();
        logger.i("QIF Import: Importing done in "+ TimeUnit.MILLISECONDS.toSeconds(t2-t1)+"s");
    }

    public void doImport(QifParser parser) {
        long t0 = System.currentTimeMillis();
        insertPayees(parser.payees);
        long t1 = System.currentTimeMillis();
        logger.i("QIF Import: Inserting payees done in " + TimeUnit.MILLISECONDS.toSeconds(t1 - t0) + "s");
        insertProjects(parser.classes);
        long t2 = System.currentTimeMillis();
        logger.i("QIF Import: Inserting projects done in "+ TimeUnit.MILLISECONDS.toSeconds(t2-t1)+"s");
        categoryCache.insertCategories(getDbAdapter(), parser.categories);
        long t3 = System.currentTimeMillis();
        logger.i("QIF Import: Inserting categories done in "+ TimeUnit.MILLISECONDS.toSeconds(t3-t2)+"s");
        insertAccounts(parser.accounts);
        long t4 = System.currentTimeMillis();
        logger.i("QIF Import: Inserting accounts done in "+ TimeUnit.MILLISECONDS.toSeconds(t4-t3)+"s");
        insertTransactions(parser.accounts);
        long t5 = System.currentTimeMillis();
        logger.i("QIF Import: Inserting transactions done in "+ TimeUnit.MILLISECONDS.toSeconds(t5-t4)+"s");
    }

    private void insertPayees(Set<String> payees) {
        for (String payee : payees) {
            Payee p = getDbAdapter().findOrInsertEntityByTitle(Payee.class, payee);
            payeeToId.put(payee, p.getId());
        }
    }

    private void insertProjects(Set<String> projects) {
        for (String project : projects) {
            Project p = getDbAdapter().findOrInsertEntityByTitle(Project.class, project);
            projectToId.put(project, p.getId());
        }
    }

    private void insertAccounts(List<QifAccount> accounts) {
        for (QifAccount account : accounts) {
            Account a = account.toAccount(options.getCurrency());
            getDbAdapter().saveAccount(a);
            account.dbAccount = a;
            accountTitleToAccount.put(account.memo, account);
        }
    }

    private void insertTransactions(List<QifAccount> accounts) {
        long t0 = System.currentTimeMillis();
        reduceTransfers(accounts);
        long t1 = System.currentTimeMillis();
        logger.i("QIF Import: Reducing transfers done in "+ TimeUnit.MILLISECONDS.toSeconds(t1-t0)+"s");
        convertUnknownTransfers(accounts);
        long t2 = System.currentTimeMillis();
        logger.i("QIF Import: Converting transfers done in "+ TimeUnit.MILLISECONDS.toSeconds(t2-t1)+"s");
        int count = accounts.size();
        for (int i=0; i<count; i++) {
            long t3 = System.currentTimeMillis();
            QifAccount account = accounts.get(i);
            Account a = account.dbAccount;
            insertTransactions(a, account.transactions);
            // this might help GC
            account.transactions.clear();
            long t4 = System.currentTimeMillis();
            logger.i("QIF Import: Inserting transactions for account "+i+"/"+count+" done in "+ TimeUnit.MILLISECONDS.toSeconds(t4-t3)+"s");
        }
    }

    private void reduceTransfers(List<QifAccount> accounts) {
        for (QifAccount fromAccount : accounts) {
            List<QifTransaction> transactions = fromAccount.transactions;
            reduceTransfers(fromAccount, transactions);
        }
    }

    private void reduceTransfers(QifAccount fromAccount, List<QifTransaction> transactions) {
        for (QifTransaction fromTransaction : transactions) {
            if (fromTransaction.isTransfer() && fromTransaction.amount < 0) {
                boolean found = false;
                QifAccount toAccount = accountTitleToAccount.get(fromTransaction.toAccount);
                if (toAccount != null) {
                    Iterator<QifTransaction> iterator = toAccount.transactions.iterator();
                    while (iterator.hasNext()) {
                        QifTransaction toTransaction = iterator.next();
                        if (twoSidesOfTheSameTransfer(fromAccount, fromTransaction, toAccount, toTransaction)) {
                            iterator.remove();
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    convertIntoRegularTransaction(fromTransaction);
                }
            }
            if (fromTransaction.splits != null) {
                reduceTransfers(fromAccount, fromTransaction.splits);
            }
        }
    }

    private void convertUnknownTransfers(List<QifAccount> accounts) {
        for (QifAccount fromAccount : accounts) {
            List<QifTransaction> transactions = fromAccount.transactions;
            convertUnknownTransfers(fromAccount, transactions);
        }
    }

    private void convertUnknownTransfers(QifAccount fromAccount/*to keep compiler happy*/, List<QifTransaction> transactions) {
        for (QifTransaction transaction : transactions) {
            if (transaction.isTransfer() && transaction.amount >= 0) {
                convertIntoRegularTransaction(transaction);
            }
            if (transaction.splits != null) {
                convertUnknownTransfers(fromAccount, transaction.splits);
            }
        }
    }

    private void convertIntoRegularTransaction(QifTransaction fromTransaction) {
        fromTransaction.memo = prependMemo("Transfer: " + fromTransaction.toAccount, fromTransaction);
        fromTransaction.toAccount = null;
    }

    private String prependMemo(String prefix, QifTransaction fromTransaction) {
        if (isEmpty(fromTransaction.memo)) {
            return prefix;
        } else {
            return prefix + " | " + fromTransaction.memo;
        }
    }

    private boolean twoSidesOfTheSameTransfer(QifAccount fromAccount, QifTransaction fromTransaction, QifAccount toAccount, QifTransaction toTransaction) {
        return toTransaction.isTransfer()
                && toTransaction.toAccount.equals(fromAccount.memo) && fromTransaction.toAccount.equals(toAccount.memo)
                && fromTransaction.date.equals(toTransaction.date) && fromTransaction.amount == -toTransaction.amount;
    }

    private void insertTransactions(Account a, List<QifTransaction> transactions) {
        for (QifTransaction transaction : transactions) {
            Transaction t = transaction.toTransaction();
            t.payeeId = findPayee(transaction.payee);
            t.projectId = findProject(transaction.categoryClass);
            t.fromAccountId = a.id;
            findToAccount(transaction, t);
            findCategory(transaction, t);
            if (transaction.splits != null) {
                List<Transaction> splits = new ArrayList<>(transaction.splits.size());
                for (QifTransaction split : transaction.splits) {
                    Transaction s = split.toTransaction();
                    findToAccount(split, s);
                    findCategory(split, s);
                    splits.add(s);
                }
                t.splits = splits;
            }
            getDbAdapter().insertWithoutUpdatingBalance(t);
        }
    }

    public long findPayee(String payee) {
        return findIdInAMap(payee, payeeToId);
    }

    private long findProject(String project) {
        return findIdInAMap(project, projectToId);
    }

    private long findIdInAMap(String project, Map<String, Long> map) {
        if (map.containsKey(project)) {
            return map.get(project);
        }
        return 0;
    }

    private void findToAccount(QifTransaction transaction, Transaction t) {
        if (transaction.isTransfer()) {
            Account toAccount = findAccount(transaction.toAccount);
            if (toAccount != null) {
                t.toAccountId = toAccount.id;
                t.toAmount = -t.fromAmount;
            }
        }
    }

    private Account findAccount(String account) {
        QifAccount a = accountTitleToAccount.get(account);
        return a != null ? a.dbAccount : null;
    }

    private void findCategory(QifTransaction transaction, Transaction t) {
        Category c = categoryCache.findCategory(transaction.category);
        if (c != null) {
            t.categoryId = c.id;
        }
    }

}
