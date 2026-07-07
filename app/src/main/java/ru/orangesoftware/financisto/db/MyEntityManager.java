package ru.orangesoftware.financisto.db;

import static ru.orangesoftware.financisto.db.DatabaseHelper.ACCOUNT_TABLE;
import static ru.orangesoftware.financisto.db.DatabaseHelper.AccountColumns;
import static ru.orangesoftware.financisto.db.DatabaseHelper.BUDGET_TABLE;
import static ru.orangesoftware.financisto.db.DatabaseHelper.CURRENCY_TABLE;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
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
import ru.orangesoftware.financisto.model.SmsTemplate;
import ru.orangesoftware.financisto.model.SystemAttribute;
import ru.orangesoftware.financisto.model.Transaction;
import ru.orangesoftware.financisto.model.TransactionAttributeInfo;
import ru.orangesoftware.financisto.model.TransactionInfo;
import ru.orangesoftware.financisto.utils.MyPreferences;
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

    public MyEntityManager(Context context) {
        super(new DependenciesHolder().getDatabaseHelper(), new DatabaseFixPlugin());
        this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id can't be null");
        }
        long entityId = (Long) id;
        if (clazz == Account.class) return (T) DatabaseMappersKt.getAccount(this, entityId);
        if (clazz == Payee.class) return (T) DatabaseMappersKt.getPayee(this, entityId);
        if (clazz == Project.class) return (T) DatabaseMappersKt.getProject(this, entityId);
        if (clazz == MyLocation.class) return (T) DatabaseMappersKt.getLocation(this, entityId);
        if (clazz == Category.class) return (T) DatabaseMappersKt.getCategory(this, entityId);
        if (clazz == Currency.class) return (T) DatabaseMappersKt.getCurrency(this, entityId);
        if (clazz == Budget.class) return (T) DatabaseMappersKt.getBudget(this, entityId);
        if (clazz == SmsTemplate.class) return (T) DatabaseMappersKt.getSmsTemplate(this, entityId);
        if (clazz == Transaction.class) return (T) DatabaseMappersKt.getTransaction(this, entityId);
        if (clazz == TransactionInfo.class) return (T) DatabaseMappersKt.getTransactionInfo(this, entityId);
        return super.get(clazz, id);
    }

    @Override
    public long saveOrUpdate(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity is null");
        }
        if (entity instanceof Account) return DatabaseMappersKt.saveAccount(this, (Account) entity);
        if (entity instanceof Payee) return DatabaseMappersKt.savePayee(this, (Payee) entity);
        if (entity instanceof Project) return DatabaseMappersKt.saveProject(this, (Project) entity);
        if (entity instanceof MyLocation) return DatabaseMappersKt.saveLocation(this, (MyLocation) entity);
        if (entity instanceof Category) return DatabaseMappersKt.saveCategory(this, (Category) entity);
        if (entity instanceof Currency) return saveOrUpdate((Currency) entity);
        if (entity instanceof Budget) return DatabaseMappersKt.saveBudget(this, (Budget) entity);
        if (entity instanceof SmsTemplate) return DatabaseMappersKt.saveSmsTemplate(this, (SmsTemplate) entity);
        if (entity instanceof Transaction) return DatabaseMappersKt.insertOrUpdate(this, (Transaction) entity);
        return super.saveOrUpdate(entity);
    }

    @Override
    public <T> int delete(Class<T> clazz, Object id) {
        if (id == null) {
            throw new IllegalArgumentException("Id can't be null");
        }
        String tableName = DatabaseMappersKt.getTableName(this, clazz);
        if (tableName != null) {
            return db().delete(tableName, "_id=?", new String[]{String.valueOf(id)});
        }
        return super.delete(clazz, id);
    }

    public <T extends MyEntity> Cursor filterActiveEntities(Class<T> clazz, String titleLike) {
        Cursor c = DatabaseMappersKt.filterActiveEntities(this, clazz, titleLike);
        if (c != null) return c;
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

    @SuppressWarnings("unchecked")
    public <T extends MyEntity> ArrayList<T> getAllEntitiesList(Class<T> clazz, boolean include0, boolean onlyActive, String filter, Sort... sort) {
        if (clazz == Account.class) return (ArrayList<T>) DatabaseMappersKt.getAccountsList(this, include0, onlyActive, filter, sort);
        if (clazz == Payee.class) return (ArrayList<T>) DatabaseMappersKt.getPayeesList(this, include0, onlyActive, filter, sort);
        if (clazz == Project.class) return (ArrayList<T>) DatabaseMappersKt.getProjectsList(this, include0, onlyActive, filter, sort);
        if (clazz == MyLocation.class) return (ArrayList<T>) DatabaseMappersKt.getLocationsList(this, include0, onlyActive, filter, sort);
        if (clazz == Category.class) return (ArrayList<T>) DatabaseMappersKt.getCategoriesList(this, include0, onlyActive, filter, sort);
        if (clazz == Currency.class) return (ArrayList<T>) DatabaseMappersKt.getCurrenciesList(this, include0, onlyActive, filter, sort);

        try (Cursor c = queryEntities(clazz, filter, include0, onlyActive, sort)) {
            T e0 = null;
            ArrayList<T> list = new ArrayList<>();
            while (c.moveToNext()) {
                T e = EntityManager.loadFromCursor(c, clazz);
                if (e.id == 0) {
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
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            delete(MyLocation.class, id);
            ContentValues values = new ContentValues();
            values.put("location_id", 0);
            db.update("transactions", values, "location_id=?", new String[]{String.valueOf(id)});
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
        return DatabaseMappersKt.getAttributesForTransaction(this, transactionId);
    }
 
    public TransactionAttributeInfo getSystemAttributeForTransaction(SystemAttribute sa, long transactionId) {
        return DatabaseMappersKt.getSystemAttributeForTransaction(this, sa, transactionId);
    }

    /* ===============================================
     * ACCOUNT
     * =============================================== */
    public Cursor getAccountByNumber(String numberEnding) {
        Query<Account> q = createQuery(Account.class);
        q.where(Expressions.like(AccountColumns.NUMBER, "%" + numberEnding));
        return q.execute();
    }

    @Nullable
    public Account getAccount(long id) {
        return get(Account.class, id);
    }

    public Cursor getAccountsForTransaction(Transaction t) {
        return getAllAccounts(true, t.fromAccountId, t.toAccountId);
    }

    public Cursor getAllActiveAccounts() {
        return getAllAccounts(true);
    }

    public Cursor getAllAccounts() {
        return getAllAccounts(false);
    }

    private Cursor getAllAccounts(boolean isActiveOnly, long... includeAccounts) {
        return DatabaseMappersKt.getAllAccountsCursor(this, isActiveOnly, includeAccounts);
    }

    public long saveAccount(Account account) {
        return saveOrUpdate(account);
    }

    public List<Account> getAllAccountsList() {
        List<Account> list = new ArrayList<>();
        try (Cursor c = getAllAccounts()) {
            while (c.moveToNext()) {
                Account a = DatabaseMappersKt.toAccount(c, this);
                list.add(a);
            }
        }
        return list;
    }

    public Map<Long, Account> getAllAccountsMap() {
        Map<Long, Account> accountsMap = new HashMap<>();
        List<Account> list = getAllAccountsList();
        for (Account account : list) {
            accountsMap.put(account.id, account);
        }
        return accountsMap;
    }

    /* ===============================================
     * CURRENCY
     * =============================================== */

    private static final String UPDATE_DEFAULT_FLAG = "update currency set is_default=0";

    public long saveOrUpdate(Currency currency) {
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            if (currency.isDefault) {
                db.execSQL(UPDATE_DEFAULT_FLAG);
            }
            long id = super.saveOrUpdate(currency);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public int deleteCurrency(long id) {
        String sid = String.valueOf(id);
        Currency c = load(Currency.class, id);
        return db().delete(CURRENCY_TABLE, "_id=? AND NOT EXISTS (SELECT 1 FROM " + ACCOUNT_TABLE + " WHERE " + AccountColumns.CURRENCY_ID + "=?)",
                new String[]{sid, sid});
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
            if (list.get(i).id == 0) {
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
        SQLiteDatabase db = db();
        budget.remoteKey = null;

        db.beginTransaction();
        try {
            if (budget.id > 0) {
                deleteBudget(budget.id);
            }
            long id = 0;
            Recur recur = RecurUtils.createFromExtraString(budget.recur);
            Period[] periods = RecurUtils.periods(recur);
            for (int i = 0; i < periods.length; i++) {
                Period p = periods[i];
                budget.id = -1;
                budget.parentBudgetId = id;
                budget.recurNum = i;
                budget.startDate = p.getStart();
                budget.endDate = p.getEnd();
                long bid = DatabaseMappersKt.saveBudget(this, budget);
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
        SQLiteDatabase db = db();
        db.delete(BUDGET_TABLE, "_id=?", new String[]{String.valueOf(id)});
        db.delete(BUDGET_TABLE, "parent_budget_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteBudgetOneEntry(long id) {
        db().delete(BUDGET_TABLE, "_id=?", new String[]{String.valueOf(id)});
    }

    public ArrayList<Budget> getAllBudgets(WhereFilter filter) {
        Criteria c = filter.get(BlotterFilter.DATETIME);
        String selection = null;
        String[] selectionArgs = null;
        if (c != null) {
            long start = c.getLongValue1();
            long end = c.getLongValue2();
            selection = "start_date <= ? AND end_date >= ?";
            selectionArgs = new String[] { String.valueOf(end), String.valueOf(start) };
        }
        
        String orderBy = "title ASC";
        switch (MyPreferences.getBudgetsSortOrder(context)) {
            case DATE:
                orderBy = "start_date DESC";
                break;
            case NAME:
                orderBy = "title ASC";
                break;
            case AMOUNT:
                orderBy = "amount DESC";
                break;
        }
        try (Cursor cursor = db().query(BUDGET_TABLE, null, selection, selectionArgs, null, null, orderBy)) {
            ArrayList<Budget> list = new ArrayList<>();
            while (cursor.moveToNext()) {
                Budget b = DatabaseMappersKt.toBudget(cursor, this);
                list.add(b);
            }
            return list;
        }
    }

    public void deleteProject(long id) {
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            delete(Project.class, id);
            ContentValues values = new ContentValues();
            values.put("project_id", 0);
            db.update("transactions", values, "project_id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public ArrayList<TransactionInfo> getAllScheduledTransactions() {
        return DatabaseMappersKt.getAllScheduledTransactionsDirect(this);
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
                e.title = title;
                e.id = saveOrUpdate(e);
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
        return DatabaseMappersKt.getSplitsForTransaction(this, transactionId);
    }

    public List<TransactionInfo> getSplitsInfoForTransaction(long transactionId) {
        return DatabaseMappersKt.getSplitsInfoForTransaction(this, transactionId);
    }

    public List<TransactionInfo> getTransactionsForAccount(long accountId) {
        return DatabaseMappersKt.getTransactionsForAccount(this, accountId);
    }

    void reInsertEntity(MyEntity e) {
        if (get(e.getClass(), e.id) == null) {
            reInsert(e);
        }
    }

    public Currency getHomeCurrency() {
        return DatabaseMappersKt.getHomeCurrencyDirect(this);
    }

    private static <T extends MyEntity> Map<String, T> entitiesAsTitleMap(List<T> entities) {
        Map<String, T> map = new HashMap<>();
        for (T e : entities) {
            map.put(e.title, e);
        }
        return map;
    }

    private static <T extends MyEntity> Map<Long, T> entitiesAsIdMap(List<T> entities) {
        Map<Long, T> map = new HashMap<>();
        for (T e : entities) {
            map.put(e.id, e);
        }
        return map;
    }

}
