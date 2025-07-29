package ru.orangesoftware.financisto.repository.local

import ru.orangesoftware.financisto.repository.local.DatabaseHelper.AccountColumns

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import ru.orangesoftware.financisto.repository.model.filter.BlotterFilter
import ru.orangesoftware.financisto.repository.model.filter.Criteria
import ru.orangesoftware.financisto.repository.model.filter.WhereFilter
import ru.orangesoftware.financisto.repository.model.Account
import ru.orangesoftware.financisto.repository.model.AccountSortOrder
import ru.orangesoftware.financisto.repository.model.Budget
import ru.orangesoftware.financisto.repository.model.BudgetsSortOrder
import ru.orangesoftware.financisto.repository.model.Category
import ru.orangesoftware.financisto.repository.model.Currency
import ru.orangesoftware.financisto.repository.model.LocationsSortOrder
import ru.orangesoftware.financisto.repository.model.MyEntity
import ru.orangesoftware.financisto.repository.model.MyLocation
import ru.orangesoftware.financisto.repository.model.Payee
import ru.orangesoftware.financisto.repository.model.Period
import ru.orangesoftware.financisto.repository.model.Project
import ru.orangesoftware.financisto.repository.model.SystemAttribute
import ru.orangesoftware.financisto.repository.model.Transaction
import ru.orangesoftware.financisto.repository.model.TransactionAttributeInfo
import ru.orangesoftware.financisto.repository.model.TransactionInfo
import ru.orangesoftware.financisto.repository.utils.RecurUtils
import ru.orangesoftware.financisto.repository.utils.RecurUtils.Recur
import ru.orangesoftware.orb.EntityManager
import ru.orangesoftware.orb.Expression
import ru.orangesoftware.orb.Expressions
import ru.orangesoftware.orb.Query
import ru.orangesoftware.orb.Sort
import java.util.Locale.getDefault
import androidx.core.database.sqlite.transaction
import ru.orangesoftware.financisto.repository.local.DatabaseHelper.Companion.ACCOUNT_TABLE
import ru.orangesoftware.financisto.repository.local.DatabaseHelper.Companion.BUDGET_TABLE
import ru.orangesoftware.financisto.repository.local.DatabaseHelper.Companion.CURRENCY_TABLE

abstract class MyEntityManager(
    databaseHelper: SQLiteOpenHelper,
    private val locationsSortOrder: LocationsSortOrder,
    private val accountSortOrder: AccountSortOrder,
    private val budgetsSortOrder: BudgetsSortOrder,
) : EntityManager(
    databaseHelper,
    DatabaseFixPlugin(),
) {

    fun <T : MyEntity> filterActiveEntities(clazz: Class<T>, titleLike: String): Cursor =
        queryEntities(clazz, titleLike, false, true)

    fun <T : MyEntity> queryEntities(
        clazz: Class<T>,
        titleLike: String?,
        include0: Boolean,
        onlyActive: Boolean,
        vararg sort: Sort,
    ): Cursor {
        var titleLike = titleLike

        val q: Query<T> = createQuery(clazz)
        val include0Ex: Expression = if (include0) {
            Expressions.gte("id", 0)
        } else {
            Expressions.gt("id", 0)
        }
        var whereEx: Expression = include0Ex
        if (onlyActive) {
            whereEx = Expressions.and(include0Ex, Expressions.eq("isActive", 1));
        }
        if (!titleLike.isNullOrEmpty()) {
            titleLike = titleLike.replace(" ", "%");
            whereEx = Expressions.and(whereEx, Expressions.or(
                    Expressions.like("title", "%$titleLike%"),
                    Expressions.like("title", "%" + titleLike.replaceFirstChar { if (it.isLowerCase()) it.titlecase(
                        getDefault()
                    ) else it.toString() } + "%")
            ));
        }
        q.where(whereEx)
        if (sort.isNotEmpty()) {
            q.sort(*sort)
        } else {
            q.asc("title")
        }
        return q.execute()
    }

    fun <T : MyEntity> getAllEntitiesList(
        clazz: Class<T>,
        include0: Boolean,
        onlyActive: Boolean,
        vararg sort: Sort,
    ): List<T> = getAllEntitiesList(clazz, include0, onlyActive, null, *sort)

    fun <T : MyEntity> getAllEntitiesList(
        clazz: Class<T>,
        include0: Boolean,
        onlyActive: Boolean,
        filter: String?,
        vararg sort: Sort,
    ): List<T> = queryEntities(clazz, filter ?: "", include0, onlyActive, *sort).use {
        var e0: T? = null
        val list: MutableList<T> = mutableListOf()
        while (it.moveToNext()) {
            val e: T? = loadFromCursor(it, clazz)
            if (e != null) {
                if (e.id == 0L) {
                    e0 = e
                } else {
                    list.add(e)
                }
            }
        }
        if (e0 != null) {
            list.add(0, e0)
        }
        list
    }

    /* ===============================================
     * LOCATION
     * =============================================== */

    fun getAllLocationsList(includeNoLocation: Boolean): List<MyLocation> {
        return getAllEntitiesList(MyLocation::class.java, includeNoLocation, false, *locationSort())
    }

    fun getAllActiveLocationsList(): List<MyLocation> {
        return getAllEntitiesList(MyLocation::class.java, true, false, *locationSort())
    }

    fun getActiveLocationsList(includeNoLocation: Boolean): List<MyLocation> {
        return getAllEntitiesList(MyLocation::class.java, includeNoLocation, true, *locationSort())
    }

    private fun locationSort(): Array<Sort> {
        val sort: MutableList<Sort> = mutableListOf()
        val sortOrder: LocationsSortOrder = locationsSortOrder
        sort.add(Sort(sortOrder.property, sortOrder.asc))
        if (sortOrder != LocationsSortOrder.TITLE) {
            sort.add(Sort(LocationsSortOrder.TITLE.property, sortOrder.asc))
        }
        return sort.toTypedArray()
    }

    fun getAllLocationsByIdMap(includeNoLocation: Boolean): Map<Long, MyLocation> {
        return entitiesAsIdMap(getAllLocationsList(includeNoLocation))
    }

    fun deleteLocation(id: Long) {
        val db:SQLiteDatabase = db()
        db.transaction {
            try {
                delete(MyLocation::class.java, id)
                val values: ContentValues = ContentValues()
                values.put("location_id", 0)
                update("transactions", values, "location_id=?", arrayOf(id.toString()))
            } finally {
            }
        }
    }

    fun saveLocation(location: MyLocation): Long {
        return saveOrUpdate(location)
    }

    /* ===============================================
     * TRANSACTION INFO
     * =============================================== */

    fun getTransactionInfo(transactionId: Long): TransactionInfo? {
        return get(TransactionInfo::class.java, transactionId)
    }

    fun getAttributesForTransaction(transactionId: Long): List<TransactionAttributeInfo?> {
        val q: Query<TransactionAttributeInfo> = createQuery(TransactionAttributeInfo::class.java).asc("name")
        q.where(Expressions.and(
                Expressions.eq("transactionId", transactionId),
                Expressions.gte("attributeId", 0),
        ))
        return q.execute().use { c ->
            val list: MutableList<TransactionAttributeInfo?> = mutableListOf()
            while (c.moveToNext()) {
                val ti: TransactionAttributeInfo? = loadFromCursor(c, TransactionAttributeInfo::class.java)
                list.add(ti)
            }
            list
        }
    }

    fun getSystemAttributeForTransaction(sa: SystemAttribute, transactionId: Long): TransactionAttributeInfo? {
        val q: Query<TransactionAttributeInfo> = createQuery(TransactionAttributeInfo::class.java)
        q.where(Expressions.and(
                Expressions.eq("transactionId", transactionId),
                Expressions.eq("attributeId", sa.id),
        ))
        return q.execute().use {
            var toRet: TransactionAttributeInfo? = null
            if (it.moveToFirst()) {
                toRet = loadFromCursor(it, TransactionAttributeInfo::class.java)
            }
            toRet
        }
    }

    /* ===============================================
     * ACCOUNT
     * =============================================== */
    fun getAccountByNumber(numberEnding: String): Cursor {
        val q: Query<Account> = createQuery(Account::class.java)
        q.where(Expressions.like(AccountColumns.NUMBER, "%$numberEnding"))
        return q.execute()
    }

    fun getAccount(id: Long): Account? {
        return get(Account::class.java, id)
    }

    fun getAccountsForTransaction(t: Transaction): Cursor {
        return getAllAccounts(true, t.fromAccountId, t.toAccountId)
    }

    fun getAllActiveAccounts(): Cursor {
        return getAllAccounts(true)
    }

    fun getAllAccounts(): Cursor {
        return getAllAccounts(false)
    }

    private fun getAllAccounts(isActiveOnly: Boolean, vararg includeAccounts: Long): Cursor {
        val sortOrder: AccountSortOrder = accountSortOrder
        val q: Query<Account> = createQuery(Account::class.java)
        if (isActiveOnly) {
            val count: Int = includeAccounts.size
            if (count > 0) {
                val ee: Array<Expression> = emptyArray()
                for (i in 0 until count) {
                    ee[i] = Expressions.eq("id", includeAccounts[i])
                }
                ee[count] = Expressions.eq("isActive", 1)
                q.where(Expressions.or(*ee))
            } else {
                q.where(Expressions.eq("isActive", 1))
            }
        }
        q.desc("isActive")
        if (sortOrder.asc) {
            q.asc(sortOrder.property)
        } else {
            q.desc(sortOrder.property)
        }
        return q.asc("title").execute()
    }

    fun saveAccount(account: Account): Long {
        return saveOrUpdate(account)
    }

    fun getAllAccountsList(): List<Account?> {
        val list: MutableList<Account?> = mutableListOf()
        getAllAccounts().use { c ->
            while (c.moveToNext()) {
                val a: Account? = loadFromCursor(c, Account::class.java)
                list.add(a)
            }
        }
        return list
    }

    fun getAllAccountsMap(): Map<Long, Account> {
        val accountsMap: MutableMap<Long, Account> = mutableMapOf()
        val list: List<Account?> = getAllAccountsList()
        list.filterNotNull().map { accountsMap.put(it.id, it) }
        return accountsMap
    }

    /* ===============================================
     * CURRENCY
     * =============================================== */

    private val UPDATE_DEFAULT_FLAG: String = "update currency set is_default=0"

    fun saveOrUpdate(currency: Currency): Long {
        val db: SQLiteDatabase = db()
        db.transaction {
            try {
                if (currency.isDefault) {
                    execSQL(UPDATE_DEFAULT_FLAG)
                }
                val id: Long = super.saveOrUpdate(currency)
                return id
            } finally {
            }
        }
    }

    fun deleteCurrency(id: Long): Int {
        val c: Currency = load(Currency::class.java, id)
        return db().delete(
            CURRENCY_TABLE,
            "_id=? AND NOT EXISTS (SELECT 1 FROM $ACCOUNT_TABLE WHERE ${AccountColumns.CURRENCY_ID}=?)",
            arrayOf(id.toString(), c.id.toString()),
        )
    }

    fun getAllCurrencies(sortBy: String): Cursor {
        val q:Query<Currency> = createQuery(Currency::class.java)
        return q.desc("isDefault").asc(sortBy).execute()
    }

    fun getAllCurrenciesList(): List<Currency> {
        return getAllCurrenciesList("name")
    }

    fun getAllCurrenciesList(sortBy: String): List<Currency> {
        val q: Query<Currency> = createQuery(Currency::class.java)
        return q.desc("isDefault").asc(sortBy).list()
    }

    fun getAllCurrenciesByTtitleMap(): Map<String, Currency> {
        return entitiesAsTitleMap(getAllCurrenciesList("name"))
    }

    /* ===============================================
     * TRANSACTIONS
     * =============================================== */

    fun getProject(id: Long): Project? {
        return get(Project::class.java, id)
    }

    fun getAllProjectsList(includeNoProject: Boolean): List<Project> {
        val list: MutableList<Project> = getAllEntitiesList(
            Project::class.java,
            includeNoProject,
            false,
            projectSort(),
        ).toMutableList()
        if (includeNoProject) {
            addZeroEntity(list, Project.noProject())
        }
        return list
    }

    fun getAllActiveProjectsList(): List<Project?> {
        return getAllEntitiesList(Project::class.java, true, true, projectSort())
    }

    fun getActiveProjectsList(includeNoProject: Boolean): List<Project?> {
        return getAllEntitiesList(Project::class.java, includeNoProject, true, projectSort())
    }

    private fun projectSort(): Sort {
        return Sort("title", true)
    }

    fun <T : MyEntity> addZeroEntity(list: MutableList<T>, zeroEntity: T) {
        val zeroPos = list.foldIndexed( -1) { index, acc, current ->
            if (current.id == 0L && acc == -1) index else acc
        }
       
        if (zeroPos >= 0) {
            list.add(0, list.removeAt(zeroPos))
        } else {
            list.add(0, zeroEntity)
        }
    }

    fun getAllProjectsByTitleMap(includeNoProject: Boolean): Map<String, Project> {
        return entitiesAsTitleMap(getAllProjectsList(includeNoProject))
    }

    fun getAllProjectsByIdMap(includeNoProject: Boolean): Map<Long, Project> {
        return entitiesAsIdMap(getAllProjectsList(includeNoProject))
    }

    fun insertBudget(budget: Budget): Long {
        val db: SQLiteDatabase = db()
        budget.remoteKey = null

        db.transaction {
            try {
                if (budget.id > 0) {
                    deleteBudget(budget.id)
                }
                var id: Long = 0
                val recur: Recur = RecurUtils.createFromExtraString(budget.recur)
                val periods: Array<Period> = RecurUtils.periods(recur)
                periods.forEachIndexed { index, period ->
                    budget.id = -1
                    budget.parentBudgetId = id
                    budget.recurNum = index.toLong()
                    budget.startDate = period.start
                    budget.endDate = period.end
                    val bid: Long = super.saveOrUpdate(budget)
                    if (index == 0) {
                        id = bid
                    }
                }
                return id
            } finally {
            }
        }
    }

    fun deleteBudget(id: Long) {
        val db: SQLiteDatabase = db()
        db.delete(
            BUDGET_TABLE,
            "_id=?",
            arrayOf(id.toString()),
        )
        db.delete(
            BUDGET_TABLE,
            "parent_budget_id=?",
            arrayOf(id.toString()),
        )
    }

    fun deleteBudgetOneEntry(id: Long) {
        db().delete(
            BUDGET_TABLE,
            "_id=?",
            arrayOf(id.toString())
        )
    }

    fun getAllBudgets(filter: WhereFilter): List<Budget?> {
        val q: Query<Budget> = createQuery(Budget::class.java)
        val c: Criteria? = filter.get(BlotterFilter.DATETIME)
        if (c != null) {
            val start: Long = c.longValue1
            val end: Long = c.longValue2
            q.where(
                Expressions.and(
                    Expressions.lte("startDate", end),
                    Expressions.gte("endDate", start),
                )
            )

            when (budgetsSortOrder) {
                BudgetsSortOrder.DATE -> q.desc("startDate")
                BudgetsSortOrder.NAME -> q.asc("title")
                BudgetsSortOrder.AMOUNT -> q.desc("amount")
            }
        }
        val list: MutableList<Budget?> = mutableListOf()
        q.execute().use {
            while (it.moveToNext()) {
                val b: Budget? = loadFromCursor(it, Budget::class.java)
                list.add(b)
            }
        }
        return list
    }

    fun deleteProject(id: Long) {
        val db: SQLiteDatabase = db()
        db.transaction {
            try {
                delete(Project::class.java, id)
                val values: ContentValues = ContentValues()
                values.put("project_id", 0)
                update(
                    "transactions",
                    values,
                    "project_id=?",
                    arrayOf(id.toString()),
                )
            } finally {
            }
        }
    }

    fun getAllScheduledTransactions(): List<TransactionInfo> {
        val q: Query<TransactionInfo> = createQuery(TransactionInfo::class.java)
        q.where(
            Expressions.and(
                Expressions.eq("isTemplate", 2),
                Expressions.eq("parentId", 0),
            )
        )
        return q.list()
    }

    fun getCategory(id: Long): Category? {
        return get(Category::class.java, id)
    }

    fun getAllCategoriesList(includeNoCategory: Boolean): List<Category?> {
        return getAllEntitiesList(Category::class.java, includeNoCategory, false)
    }

    fun <T : MyEntity> findOrInsertEntityByTitle(entityClass: Class<T>, title: String): T {
        if (title.isEmpty()) {
            return newEntity(entityClass)
        } else {
            var e: T? = findEntityByTitle(entityClass, title)
            if (e == null) {
                e = newEntity(entityClass)
                e.title = title
                e.id = saveOrUpdate(e)
            }
            return e
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun <T : MyEntity> newEntity(entityClass: Class<T>): T {
        try {
            return entityClass.getDeclaredConstructor().newInstance()
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException(e)
        }
    }

    fun <T : MyEntity> findEntityByTitle(entityClass: Class<T>, title: String): T? {
        val q: Query<T> = createQuery(entityClass)
        q.where(Expressions.eq("title", title))
        return q.uniqueResult()
    }

    fun <T : MyEntity> getAllEntities(entityClass: Class<T>): Cursor {
        return queryEntities(
            entityClass,
            null,
            false,
            false,
        )
    }

    fun getAllPayeeList(): List<Payee> {
        return getAllEntitiesList(Payee::class.java, true, false, payeeSort())
    }

    fun getAllActivePayeeList(): List<Payee?> {
        return getAllEntitiesList(Payee::class.java, true, true, payeeSort())
    }

    private fun payeeSort(): Sort {
        return Sort("title", true)
    }

    fun getAllPayeeByTitleMap(): Map<String, Payee> {
        return entitiesAsTitleMap(getAllPayeeList())
    }

    fun getAllPayeeByIdMap(): Map<Long, Payee> {
        return entitiesAsIdMap(getAllPayeeList())
    }

    fun getAllPayeesLike(constraint: String): Cursor {
        return filterAllEntities(Payee::class.java, constraint)
    }

    fun <T : MyEntity> filterAllEntities(entityClass: Class<T>, titleFilter: String?): Cursor {
        return queryEntities(
            entityClass,
            titleFilter.orEmpty(),
            false,
            false,
        )
    }

    fun getSplitsForTransaction(transactionId: Long): List<Transaction> {
        val q: Query<Transaction> = createQuery(Transaction::class.java)
        q.where(Expressions.eq("parentId", transactionId))
        return q.list()
    }

    fun getSplitsInfoForTransaction(transactionId: Long): List<TransactionInfo> {
        val q: Query<TransactionInfo> = createQuery(TransactionInfo::class.java)
        q.where(Expressions.eq("parentId", transactionId))
        return q.list()
    }

    fun getTransactionsForAccount(accountId: Long): List<TransactionInfo> {
        val q: Query<TransactionInfo> = createQuery(TransactionInfo::class.java)
        q.where(
            Expressions.and(
                Expressions.eq("fromAccount.id", accountId),
                Expressions.eq("parentId", 0)
            )
        )
        q.desc("dateTime")
        return q.list()
    }

    private fun reInsertEntity(e: MyEntity) {
        if (get(e.javaClass, e.id) == null) {
            reInsert(e)
        }
    }

    fun getHomeCurrency(): Currency {
        val q: Query<Currency> = createQuery(Currency::class.java)
        q.where(Expressions.eq("isDefault", "1")) //uh-oh
        val homeCurrency: Currency = q.uniqueResult() ?: Currency.EMPTY
        return homeCurrency
    }

    companion object {
        private fun <T : MyEntity> entitiesAsTitleMap(entities: List<T>): Map<String, T> {
            return entities.associateBy { it.title }
        }

        private fun <T : MyEntity> entitiesAsIdMap(entities: List<T>): Map<Long, T> {
            return entities.associateBy { it.id }
        }
    }
}
