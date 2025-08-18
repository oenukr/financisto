package ru.orangesoftware.financisto.db;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_NONE;
import static ru.orangesoftware.financisto.db.DatabaseHelper.ACCOUNT_TABLE;
import static ru.orangesoftware.financisto.db.DatabaseHelper.BUDGET_TABLE;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
import ru.orangesoftware.financisto.datetime.Period;
import ru.orangesoftware.financisto.filter.Criteria;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Budget;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.model.MyEntity;
import ru.orangesoftware.financisto.model.MyLocation;
import ru.orangesoftware.financisto.model.Payee;
import ru.orangesoftware.financisto.model.Project;
import ru.orangesoftware.financisto.model.SystemAttribute;
import ru.orangesoftware.financisto.model.Transaction;
import ru.orangesoftware.financisto.model.TransactionAttributeInfo;
import ru.orangesoftware.financisto.model.TransactionInfo;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.MyPreferences.AccountSortOrder;
import ru.orangesoftware.financisto.utils.MyPreferences.LocationsSortOrder;
import ru.orangesoftware.financisto.utils.RecurUtils;
import ru.orangesoftware.financisto.utils.RecurUtils.Recur;
import ru.orangesoftware.financisto.utils.StringUtil;
import ru.orangesoftware.financisto.utils.Utils;
import ru.orangesoftware.orb.EntityManager;
import ru.orangesoftware.orb.Expression;
import ru.orangesoftware.orb.Expressions;
import ru.orangesoftware.orb.Query;
import ru.orangesoftware.orb.Sort;

public abstract class MyEntityManager extends EntityManager {

    protected final Context context;
    protected final FinancistoDatabase db;

    public MyEntityManager(Context context) {
        super(new DependenciesHolder().getDatabaseHelper(), new DatabaseFixPlugin());
        this.context = context;
        this.db = Room.databaseBuilder(context.getApplicationContext(),
                FinancistoDatabase.class, "financisto.db").build();
    }

    public Context getContext() {
        return this.context;
    }

    public <T extends MyEntity> Cursor filterActiveEntities(Class<T> clazz, String titleLike) {
        return queryEntities(clazz, titleLike, false, true);
    }

    public <T extends MyEntity> Cursor queryEntities(Class<T> clazz, String titleLike, boolean include0, boolean onlyActive, Sort... sort) {
        Query<T> q = createQuery(clazz);
        Expression include0Ex = include0 ? Expressions.gte("id", 0) : Expressions.gt("id", 0);
        Expression whereEx = include0Ex;
        if (onlyActive) {
            whereEx = Expressions.and(include0Ex, Expressions.eq("isActive", 1));
        }
        if (!StringUtil.INSTANCE.isEmpty(titleLike)) {
            titleLike = titleLike.replace(" ", "%");
            whereEx = Expressions.and(whereEx, Expressions.or(
                    Expressions.like("title", "%" + titleLike + "%"),
                    Expressions.like("title", "%" + StringUtil.INSTANCE.capitalize(titleLike) + "%")
            ));
        }
        q.where(whereEx);
        if (sort != null && sort.length > 0) {
            q.sort(sort);
        } else {
            q.asc("title");
        }
        return q.execute();
    }

    public <T extends MyEntity> ArrayList<T> getAllEntitiesList(Class<T> clazz, boolean include0, boolean onlyActive, Sort... sort) {
        return getAllEntitiesList(clazz, include0, onlyActive, null, sort);
    }

    public <T extends MyEntity> ArrayList<T> getAllEntitiesList(Class<T> clazz, boolean include0, boolean onlyActive, String filter, Sort... sort) {
        try (Cursor c = queryEntities(clazz, filter, include0, onlyActive, sort)) {
            T e0 = null;
            ArrayList<T> list = new ArrayList<>();
            while (c.moveToNext()) {
                T e = EntityManager.loadFromCursor(c, clazz);
                if (e.getId() == 0) {
                    e0 = e;
                } else {
                    list.add(e);
                }
            }
            if (e0 != null) {
                list.add(0, e0);
            }
            return list;
        }
    }

    /* ===============================================
     * LOCATION
     * =============================================== */

    public ArrayList<MyLocation> getAllLocationsList(boolean includeNoLocation) {
        return getAllEntitiesList(MyLocation.class, includeNoLocation, false, locationSort());
    }

    public ArrayList<MyLocation> getAllActiveLocationsList() {
        return getAllEntitiesList(MyLocation.class, true, false, locationSort());
    }

    public ArrayList<MyLocation> getActiveLocationsList(boolean includeNoLocation) {
        return getAllEntitiesList(MyLocation.class, includeNoLocation, true, locationSort());
    }

    private Sort[] locationSort() {
        List<Sort> sort = new ArrayList<>();
        LocationsSortOrder sortOrder = MyPreferences.getLocationsSortOrder(context);
        sort.add(new Sort(sortOrder.property, sortOrder.asc));
        if (sortOrder != LocationsSortOrder.TITLE) {
            sort.add(new Sort(LocationsSortOrder.TITLE.property, sortOrder.asc));
        }
        return sort.toArray(new Sort[0]);
    }

    public Map<Long, MyLocation> getAllLocationsByIdMap(boolean includeNoLocation) {
        return entitiesAsIdMap(getAllLocationsList(includeNoLocation));
    }

    public void deleteLocation(long id) {
        SupportSQLiteDatabase db = db();
        db.beginTransaction();
        try {
            delete(MyLocation.class, id);
            ContentValues values = new ContentValues();
            values.put("location_id", 0);
            db.update("transactions", CONFLICT_NONE, values, "location_id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public long saveLocation(MyLocation location) {
        return saveOrUpdate(location);
    }

    /* ===============================================
     * TRANSACTION INFO
     * =============================================== */

    public TransactionInfo getTransactionInfo(long transactionId) {
        return get(TransactionInfo.class, transactionId);
    }

    public List<TransactionAttributeInfo> getAttributesForTransaction(long transactionId) {
        Query<TransactionAttributeInfo> q = createQuery(TransactionAttributeInfo.class).asc("name");
        q.where(Expressions.and(
                Expressions.eq("transactionId", transactionId),
                Expressions.gte("attributeId", 0)
        ));
        try (Cursor c = q.execute()) {
            List<TransactionAttributeInfo> list = new LinkedList<>();
            while (c.moveToNext()) {
                TransactionAttributeInfo ti = loadFromCursor(c, TransactionAttributeInfo.class);
                list.add(ti);
            }
            return list;
        }

    }

    public TransactionAttributeInfo getSystemAttributeForTransaction(SystemAttribute sa, long transactionId) {
        Query<TransactionAttributeInfo> q = createQuery(TransactionAttributeInfo.class);
        q.where(Expressions.and(
                Expressions.eq("transactionId", transactionId),
                Expressions.eq("attributeId", sa.getId())
        ));
        try (Cursor c = q.execute()) {
            if (c.moveToFirst()) {
                return loadFromCursor(c, TransactionAttributeInfo.class);
            }
            return null;
        }
    }

    /* ===============================================
     * ACCOUNT
     * =============================================== */
    public List<Account> getAccountByNumber(String numberEnding) {
        return db.accountDao().getAccountByNumber(numberEnding);
    }

    @Nullable
    public Account getAccount(long id) {
        return db.accountDao().get(id);
    }

    public List<Account> getAccountsForTransaction(Transaction t) {
        return getAccounts(true, t.fromAccountId, t.toAccountId);
    }

    public List<Account> getAllActiveAccounts() {
        return getAccounts(true);
    }

    public List<Account> getAllAccounts() {
        return getAccounts(false);
    }

    private List<Account> getAccounts(boolean isActiveOnly, long... includeAccounts) {
        AccountSortOrder sortOrder = MyPreferences.getAccountSortOrder(context);
        StringBuilder query = new StringBuilder("SELECT * FROM " + ACCOUNT_TABLE);
        ArrayList<Object> args = new ArrayList<>();
        if (isActiveOnly) {
            query.append(" WHERE ");
            if (includeAccounts.length > 0) {
                query.append("_id IN (");
                for (int i = 0; i < includeAccounts.length; i++) {
                    query.append("?");
                    if (i < includeAccounts.length - 1) {
                        query.append(",");
                    }
                    args.add(includeAccounts[i]);
                }
                query.append(") OR ");
            }
            query.append("is_active = 1");
        }
        query.append(" ORDER BY is_active DESC, ");
        query.append(sortOrder.property);
        if (!sortOrder.asc) {
            query.append(" DESC");
        }
        query.append(", title ASC");
        return db.accountDao().getAccounts(new SimpleSQLiteQuery(query.toString(), args.toArray()));
    }

    public long saveAccount(Account account) {
        if (account.getId() > 0) {
            db.accountDao().update(account);
        } else {
            account.setId(db.accountDao().insert(account));
        }
        return account.getId();
    }

    public List<Account> getAllAccountsList() {
        return db.accountDao().getAll();
    }

    public Map<Long, Account> getAllAccountsMap() {
        Map<Long, Account> accountsMap = new HashMap<>();
        List<Account> list = getAllAccountsList();
        for (Account account : list) {
            accountsMap.put(account.getId(), account);
        }
        return accountsMap;
    }

    /* ===============================================
     * CURRENCY
     * =============================================== */

    private static final String UPDATE_DEFAULT_FLAG = "update currency set is_default=0";

    public long saveOrUpdate(Currency currency) {
        db.runInTransaction(() -> {
            if (currency.isDefault()) {
                db.currencyDao().clearDefaults();
            }
            if (currency.getId() > 0) {
                db.currencyDao().update(currency);
            } else {
                currency.setId(db.currencyDao().insert(currency));
            }
        });
        return currency.getId();
    }

    public int deleteCurrency(long id) {
        Currency currency = db.currencyDao().get(id);
        if (currency != null) {
            db.currencyDao().delete(currency);
            return 1;
        }
        return 0;
    }

    public Currency getCurrency(long id) {
        return db.currencyDao().get(id);
    }

    public Cursor getAllCurrencies(String sortBy) {
        Query<Currency> q = createQuery(Currency.class);
        return q.desc("isDefault").asc(sortBy).execute();
    }

    public List<Currency> getAllCurrenciesList() {
        return getAllCurrenciesList("name");
    }

    public List<Currency> getAllCurrenciesList(String sortBy) {
        Query<Currency> q = createQuery(Currency.class);
        return q.desc("isDefault").asc(sortBy).list();
    }

    public Map<String, Currency> getAllCurrenciesByTtitleMap() {
        return entitiesAsTitleMap(getAllCurrenciesList("name"));
    }

    /* ===============================================
     * TRANSACTIONS
     * =============================================== */

    public Project getProject(long id) {
        return get(Project.class, id);
    }

    public ArrayList<Project> getAllProjectsList(boolean includeNoProject) {
        ArrayList<Project> list = getAllEntitiesList(Project.class, includeNoProject, false, projectSort());
        if (includeNoProject) {
            addZeroEntity(list, Project.noProject());
        }
        return list;
    }

    public List<Project> getAllActiveProjectsList() {
        return getAllEntitiesList(Project.class, true, true, projectSort());
    }

    public ArrayList<Project> getActiveProjectsList(boolean includeNoProject) {
        return getAllEntitiesList(Project.class, includeNoProject, true, projectSort());
    }

    private Sort projectSort() {
        return new Sort("title", true);
    }

    private <T extends MyEntity> void addZeroEntity(ArrayList<T> list, T zeroEntity) {
        int zeroPos = -1;
        for (int i=0; i<list.size(); i++) {
            if (list.get(i).getId() == 0) {
                zeroPos = i;
                break;
            }
        }
        if (zeroPos >= 0) {
            list.add(0, list.remove(zeroPos));
        } else {
            list.add(0, zeroEntity);
        }
    }

    public Map<String, Project> getAllProjectsByTitleMap(boolean includeNoProject) {
        return entitiesAsTitleMap(getAllProjectsList(includeNoProject));
    }

    public Map<Long, Project> getAllProjectsByIdMap(boolean includeNoProject) {
        return entitiesAsIdMap(getAllProjectsList(includeNoProject));
    }

    public long insertBudget(Budget budget) {
        SupportSQLiteDatabase db = db();
        budget.setRemoteKey(null);

        db.beginTransaction();
        try {
            if (budget.getId() > 0) {
                deleteBudget(budget.getId());
            }
            long id = 0;
            Recur recur = RecurUtils.createFromExtraString(budget.getRecur());
            Period[] periods = RecurUtils.periods(recur);
            for (int i = 0; i < periods.length; i++) {
                Period p = periods[i];
                budget.setId(-1);
                budget.setParentBudgetId(id);
                budget.setRecurNum(i);
                budget.setStartDate(p.getStart());
                budget.setEndDate(p.getEnd());
                long bid = super.saveOrUpdate(budget);
                if (i == 0) {
                    id = bid;
                }
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void deleteBudget(long id) {
        SupportSQLiteDatabase db = db();
        db.delete(BUDGET_TABLE, "_id=?", new String[]{String.valueOf(id)});
        db.delete(BUDGET_TABLE, "parent_budget_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteBudgetOneEntry(long id) {
        db().delete(BUDGET_TABLE, "_id=?", new String[]{String.valueOf(id)});
    }

    public ArrayList<Budget> getAllBudgets(WhereFilter filter) {
        Query<Budget> q = createQuery(Budget.class);
        Criteria c = filter.get(BlotterFilter.DATETIME);
        if (c != null) {
            long start = c.getLongValue1();
            long end = c.getLongValue2();
            q.where(Expressions.and(Expressions.lte("startDate", end), Expressions.gte("endDate", start)));

            switch (MyPreferences.getBudgetsSortOrder(context)) {
                case DATE:
                    q.desc("startDate");
                    break;

                case NAME:
                    q.asc("title");
                    break;

                case AMOUNT:
                    q.desc("amount");
                    break;
            }
        }
        try (Cursor cursor = q.execute()) {
            ArrayList<Budget> list = new ArrayList<>();
            while (cursor.moveToNext()) {
                Budget b = MyEntityManager.loadFromCursor(cursor, Budget.class);
                list.add(b);
            }
            return list;
        }
    }

    public void deleteProject(long id) {
        SupportSQLiteDatabase db = db();
        db.beginTransaction();
        try {
            delete(Project.class, id);
            ContentValues values = new ContentValues();
            values.put("project_id", 0);
            db.update("transactions", CONFLICT_NONE, values, "project_id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public ArrayList<TransactionInfo> getAllScheduledTransactions() {
        Query<TransactionInfo> q = createQuery(TransactionInfo.class);
        q.where(Expressions.and(
                Expressions.eq("isTemplate", 2),
                Expressions.eq("parentId", 0)));
        return (ArrayList<TransactionInfo>) q.list();
    }

    @Nullable
    public Category getCategory(long id) {
        return get(Category.class, id);
    }

    public ArrayList<Category> getAllCategoriesList(boolean includeNoCategory) {
        return getAllEntitiesList(Category.class, includeNoCategory, false);
    }

    public <T extends MyEntity> T findOrInsertEntityByTitle(Class<T> entityClass, String title) {
        if (Utils.isEmpty(title)) {
            return newEntity(entityClass);
        } else {
            T e = findEntityByTitle(entityClass, title);
            if (e == null) {
                e = newEntity(entityClass);
                e.setTitle(title);
                e.setId(saveOrUpdate(e));
            }
            return e;
        }
    }

    private <T extends MyEntity> T newEntity(Class<T> entityClass) {
        try {
            return entityClass.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public <T extends MyEntity> T findEntityByTitle(Class<T> entityClass, String title) {
        Query<T> q = createQuery(entityClass);
        q.where(Expressions.eq("title", title));
        return q.uniqueResult();
    }

    public <T extends MyEntity> Cursor getAllEntities(Class<T> entityClass) {
        return queryEntities(entityClass, null, false, false);
    }

    public List<Payee> getAllPayeeList() {
        return getAllEntitiesList(Payee.class, true, false, payeeSort());
    }

    public List<Payee> getAllActivePayeeList() {
        return getAllEntitiesList(Payee.class, true, true, payeeSort());
    }

    private Sort payeeSort() {
        return new Sort("title", true);
    }

    public Map<String, Payee> getAllPayeeByTitleMap() {
        return entitiesAsTitleMap(getAllPayeeList());
    }

    public Map<Long, Payee> getAllPayeeByIdMap() {
        return entitiesAsIdMap(getAllPayeeList());
    }

    public Cursor getAllPayeesLike(String constraint) {
        return filterAllEntities(Payee.class, constraint);
    }

    public <T extends MyEntity> Cursor filterAllEntities(Class<T> entityClass, @Nullable String titleFilter) {
        return queryEntities(entityClass, StringUtil.INSTANCE.emptyIfNull(titleFilter), false, false);
    }

    public List<Transaction> getSplitsForTransaction(long transactionId) {
        Query<Transaction> q = createQuery(Transaction.class);
        q.where(Expressions.eq("parentId", transactionId));
        return q.list();
    }

    public List<TransactionInfo> getSplitsInfoForTransaction(long transactionId) {
        Query<TransactionInfo> q = createQuery(TransactionInfo.class);
        q.where(Expressions.eq("parentId", transactionId));
        return q.list();
    }

    public List<TransactionInfo> getTransactionsForAccount(long accountId) {
        Query<TransactionInfo> q = createQuery(TransactionInfo.class);
        q.where(Expressions.and(
                Expressions.eq("fromAccount.id", accountId),
                Expressions.eq("parentId", 0)
        ));
        q.desc("dateTime");
        return q.list();
    }

    void reInsertEntity(MyEntity e) {
        if (get(e.getClass(), e.getId()) == null) {
            reInsert(e);
        }
    }

    public Currency getHomeCurrency() {
        Query<Currency> q = createQuery(Currency.class);
        q.where(Expressions.eq("isDefault", "1")); //uh-oh
        Currency homeCurrency = q.uniqueResult();
        if (homeCurrency == null) {
            homeCurrency = Currency.Companion.getEMPTY();
        }
        return homeCurrency;
    }

    private static <T extends MyEntity> Map<String, T> entitiesAsTitleMap(List<T> entities) {
        Map<String, T> map = new HashMap<>();
        for (T e : entities) {
            map.put(e.getTitle(), e);
        }
        return map;
    }

    private static <T extends MyEntity> Map<Long, T> entitiesAsIdMap(List<T> entities) {
        Map<Long, T> map = new HashMap<>();
        for (T e : entities) {
            map.put(e.getId(), e);
        }
        return map;
    }

}
