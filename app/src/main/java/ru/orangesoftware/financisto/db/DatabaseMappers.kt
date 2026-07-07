package ru.orangesoftware.financisto.db

import android.content.ContentValues
import android.database.Cursor
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Budget
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.MyLocation
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.SmsTemplate
import ru.orangesoftware.financisto.model.SymbolFormat
import ru.orangesoftware.financisto.model.SystemAttribute
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionAttributeInfo
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.StringUtil
import ru.orangesoftware.orb.Sort

// Safe Cursor Helpers
fun Cursor.getStringSafe(columnName: String): String? {
    val idx = getColumnIndex(columnName)
    return if (idx >= 0 && !isNull(idx)) getString(idx) else null
}

fun Cursor.getLongSafe(columnName: String, defaultValue: Long = 0L): Long {
    val idx = getColumnIndex(columnName)
    return if (idx >= 0 && !isNull(idx)) getLong(idx) else defaultValue
}

fun Cursor.getIntSafe(columnName: String, defaultValue: Int = 0): Int {
    val idx = getColumnIndex(columnName)
    return if (idx >= 0 && !isNull(idx)) getInt(idx) else defaultValue
}

fun Cursor.getFloatSafe(columnName: String, defaultValue: Float = 0f): Float {
    val idx = getColumnIndex(columnName)
    return if (idx >= 0 && !isNull(idx)) getFloat(idx) else defaultValue
}

fun Cursor.getDoubleSafe(columnName: String, defaultValue: Double = 0.0): Double {
    val idx = getColumnIndex(columnName)
    return if (idx >= 0 && !isNull(idx)) getDouble(idx) else defaultValue
}

fun Cursor.getBooleanSafe(columnName: String, defaultValue: Boolean = false): Boolean {
    val idx = getColumnIndex(columnName)
    return if (idx >= 0 && !isNull(idx)) getInt(idx) != 0 else defaultValue
}

// -------------------------------------------------------------------
// Entity Mappers from Cursor
// -------------------------------------------------------------------

fun Cursor.toAccount(em: MyEntityManager): Account {
    val a = Account()
    a.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    a.title = getStringSafe("title") ?: getStringSafe("e_title")
    a.creationDate = getLongSafe("creation_date", getLongSafe("e_creation_date", 0L))
    a.lastTransactionDate = getLongSafe("last_transaction_date", getLongSafe("e_last_transaction_date", 0L))
    
    val currencyId = getLongSafe("currency_id", getLongSafe("e_currency_id", 0L))
    a.currency = if (currencyId > 0) CurrencyCache.getCurrency(em, currencyId) else null
    
    a.type = getStringSafe("type") ?: getStringSafe("e_type")
    a.cardIssuer = getStringSafe("card_issuer") ?: getStringSafe("e_card_issuer")
    a.issuer = getStringSafe("issuer") ?: getStringSafe("e_issuer")
    a.number = getStringSafe("number") ?: getStringSafe("e_number")
    a.totalAmount = getLongSafe("total_amount", getLongSafe("e_total_amount", 0L))
    a.limitAmount = getLongSafe("total_limit", getLongSafe("e_total_limit", 0L))
    a.sortOrder = getIntSafe("sort_order", getIntSafe("e_sort_order", 0))
    a.isIncludeIntoTotals = getBooleanSafe("is_include_into_totals", getBooleanSafe("e_is_include_into_totals", true))
    a.lastAccountId = getLongSafe("last_account_id", getLongSafe("e_last_account_id", 0L))
    a.lastCategoryId = getLongSafe("last_category_id", getLongSafe("e_last_category_id", 0L))
    a.closingDay = getIntSafe("closing_day", getIntSafe("e_closing_day", 0))
    a.paymentDay = getIntSafe("payment_day", getIntSafe("e_payment_day", 0))
    a.note = getStringSafe("note") ?: getStringSafe("e_note")
    a.isActive = getBooleanSafe("is_active", getBooleanSafe("e_is_active", true))
    return a
}

fun Cursor.toPayee(): Payee {
    val p = Payee()
    p.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    p.title = getStringSafe("title") ?: getStringSafe("e_title")
    p.lastCategoryId = getLongSafe("last_category_id", getLongSafe("e_last_category_id", 0L))
    p.sortOrder = getLongSafe("sort_order", getLongSafe("e_sort_order", 0L))
    p.isActive = getBooleanSafe("is_active", getBooleanSafe("e_is_active", true))
    return p
}

fun Cursor.toProject(): Project {
    val p = Project()
    p.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    p.title = getStringSafe("title") ?: getStringSafe("e_title")
    p.sortOrder = getLongSafe("sort_order", getLongSafe("e_sort_order", 0L))
    p.isActive = getBooleanSafe("is_active", getBooleanSafe("e_is_active", true))
    return p
}

fun Cursor.toMyLocation(): MyLocation {
    val loc = MyLocation()
    loc.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    loc.title = getStringSafe("title") ?: getStringSafe("e_title")
    loc.provider = getStringSafe("provider") ?: getStringSafe("e_provider")
    loc.accuracy = getFloatSafe("accuracy", getFloatSafe("e_accuracy", 0f))
    loc.longitude = getDoubleSafe("longitude", getDoubleSafe("e_longitude", 0.0))
    loc.latitude = getDoubleSafe("latitude", getDoubleSafe("e_latitude", 0.0))
    loc.resolvedAddress = getStringSafe("resolved_address") ?: getStringSafe("e_resolved_address")
    loc.dateTime = getLongSafe("datetime", getLongSafe("e_datetime", 0L))
    loc.count = getIntSafe("count", getIntSafe("e_count", 0))
    loc.sortOrder = getLongSafe("sort_order", getLongSafe("e_sort_order", 0L))
    loc.isActive = getBooleanSafe("is_active", getBooleanSafe("e_is_active", true))
    return loc
}

fun Cursor.toBudget(em: MyEntityManager): Budget {
    val b = Budget()
    b.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    b.title = getStringSafe("title") ?: getStringSafe("e_title")
    b.categories = getStringSafe("category_id") ?: getStringSafe("e_category_id")
    b.projects = getStringSafe("project_id") ?: getStringSafe("e_project_id")
    b.currencyId = getLongSafe("currency_id", getLongSafe("e_currency_id", -1L))
    
    val budgetCurrencyId = getLongSafe("budget_currency_id", getLongSafe("e_budget_currency_id", 0L))
    b.currency = if (budgetCurrencyId > 0) CurrencyCache.getCurrency(em, budgetCurrencyId) else null
    
    val budgetAccountId = getLongSafe("budget_account_id", getLongSafe("e_budget_account_id", 0L))
    b.account = if (budgetAccountId > 0) em.getAccount(budgetAccountId) else null
    
    b.amount = getLongSafe("amount", getLongSafe("e_amount", 0L))
    b.includeSubcategories = getBooleanSafe("include_subcategories", getBooleanSafe("e_include_subcategories", false))
    b.expanded = getBooleanSafe("expanded", getBooleanSafe("e_expanded", false))
    b.includeCredit = getBooleanSafe("include_credit", getBooleanSafe("e_include_credit", true))
    b.startDate = getLongSafe("start_date", getLongSafe("e_start_date", 0L))
    b.endDate = getLongSafe("end_date", getLongSafe("e_end_date", 0L))
    b.recur = getStringSafe("recur") ?: getStringSafe("e_recur")
    b.recurNum = getLongSafe("recur_num", getLongSafe("e_recur_num", 0L))
    b.isCurrent = getBooleanSafe("is_current", getBooleanSafe("e_is_current", false))
    b.parentBudgetId = getLongSafe("parent_budget_id", getLongSafe("e_parent_budget_id", 0L))
    b.updatedOn = getLongSafe("updated_on", getLongSafe("e_updated_on", 0L))
    b.remoteKey = getStringSafe("remote_key") ?: getStringSafe("e_remote_key")
    b.sortOrder = getLongSafe("sort_order", getLongSafe("e_sort_order", 0L))
    return b
}

fun Cursor.toCurrency(): Currency {
    val cur = Currency()
    cur.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    cur.title = getStringSafe("title") ?: getStringSafe("e_title")
    cur.name = getStringSafe("name") ?: getStringSafe("e_name")
    cur.symbol = getStringSafe("symbol") ?: getStringSafe("e_symbol")
    val symbolFormatStr = getStringSafe("symbol_format") ?: getStringSafe("e_symbol_format")
    cur.symbolFormat = if (symbolFormatStr != null) {
        try { SymbolFormat.valueOf(symbolFormatStr) } catch (e: Exception) { SymbolFormat.RS }
    } else {
        SymbolFormat.RS
    }
    cur.isDefault = getBooleanSafe("is_default", getBooleanSafe("e_is_default", false))
    cur.decimals = getIntSafe("decimals", getIntSafe("e_decimals", 2))
    cur.decimalSeparator = getStringSafe("decimal_separator") ?: getStringSafe("e_decimal_separator")
    cur.groupSeparator = getStringSafe("group_separator") ?: getStringSafe("e_group_separator")
    cur.sortOrder = getLongSafe("sort_order", getLongSafe("e_sort_order", 0L))
    cur.isActive = getBooleanSafe("is_active", getBooleanSafe("e_is_active", true))
    return cur
}

fun Cursor.toTransaction(): Transaction {
    val t = Transaction()
    t.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    t.parentId = getLongSafe("parent_id", getLongSafe("e_parent_id", 0L))
    t.categoryId = getLongSafe("category_id", getLongSafe("e_category_id", 0L))
    t.projectId = getLongSafe("project_id", getLongSafe("e_project_id", 0L))
    t.locationId = getLongSafe("location_id", getLongSafe("e_location_id", 0L))
    t.fromAccountId = getLongSafe("from_account_id", getLongSafe("e_from_account_id", 0L))
    t.toAccountId = getLongSafe("to_account_id", getLongSafe("e_to_account_id", 0L))
    t.payeeId = getLongSafe("payee_id", getLongSafe("e_payee_id", 0L))
    t.blobKey = getStringSafe("blob_key") ?: getStringSafe("e_blob_key")
    t.originalCurrencyId = getLongSafe("original_currency_id", getLongSafe("e_original_currency_id", 0L))
    t.dateTime = getLongSafe("datetime", getLongSafe("e_datetime", 0L))
    t.provider = getStringSafe("provider") ?: getStringSafe("e_provider")
    t.accuracy = getFloatSafe("accuracy", getFloatSafe("e_accuracy", 0f))
    t.longitude = getDoubleSafe("longitude", getDoubleSafe("e_longitude", 0.0))
    t.latitude = getDoubleSafe("latitude", getDoubleSafe("e_latitude", 0.0))
    t.note = getStringSafe("note") ?: getStringSafe("e_note")
    t.originalFromAmount = getLongSafe("original_from_amount", getLongSafe("e_original_from_amount", 0L))
    t.fromAmount = getLongSafe("from_amount", getLongSafe("e_from_amount", 0L))
    t.toAmount = getLongSafe("to_amount", getLongSafe("e_to_amount", 0L))
    t.isTemplate = getIntSafe("is_template", getIntSafe("e_is_template", 0))
    t.templateName = getStringSafe("template_name") ?: getStringSafe("e_template_name")
    t.recurrence = getStringSafe("recurrence") ?: getStringSafe("e_recurrence")
    t.notificationOptions = getStringSafe("notification_options") ?: getStringSafe("e_notification_options")
    val statusStr = getStringSafe("status") ?: getStringSafe("e_status")
    t.status = if (statusStr != null) {
        try { TransactionStatus.valueOf(statusStr) } catch (e: Exception) { TransactionStatus.UR }
    } else {
        TransactionStatus.UR
    }
    t.attachedPicture = getStringSafe("attached_picture") ?: getStringSafe("e_attached_picture")
    t.isCCardPayment = getIntSafe("is_ccard_payment", getIntSafe("e_is_ccard_payment", 0))
    t.lastRecurrence = getLongSafe("last_recurrence", getLongSafe("e_last_recurrence", 0L))
    t.updatedOn = getLongSafe("updated_on", getLongSafe("e_updated_on", 0L))
    t.remoteKey = getStringSafe("remote_key") ?: getStringSafe("e_remote_key")
    return t
}

fun Cursor.toTransactionAttributeInfo(): TransactionAttributeInfo {
    val info = TransactionAttributeInfo()
    info.transactionId = getLongSafe("_id", getLongSafe("e__id", getLongSafe("transaction_id", -1L)))
    info.attributeId = getLongSafe("attribute_id", getLongSafe("e_attribute_id", -1L))
    info.type = getIntSafe("attribute_type", getIntSafe("e_attribute_type", 0))
    info.name = getStringSafe("attribute_name") ?: getStringSafe("e_attribute_name")
    info.value = getStringSafe("attribute_value") ?: getStringSafe("e_attribute_value")
    info.listValues = getStringSafe("attribute_list_values") ?: getStringSafe("e_attribute_list_values")
    return info
}

fun Cursor.toTransactionInfo(em: MyEntityManager): TransactionInfo {
    val info = TransactionInfo()
    info.id = getLongSafe("_id", getLongSafe("e__id", -1L))
    info.parentId = getLongSafe("parent_id", getLongSafe("e_parent_id", 0L))
    info.dateTime = getLongSafe("datetime", getLongSafe("e_datetime", 0L))
    info.provider = getStringSafe("provider") ?: getStringSafe("e_provider")
    info.accuracy = getFloatSafe("accuracy", getFloatSafe("e_accuracy", 0f))
    info.longitude = getDoubleSafe("longitude", getDoubleSafe("e_longitude", 0.0))
    info.latitude = getDoubleSafe("latitude", getDoubleSafe("e_latitude", 0.0))
    info.note = getStringSafe("note") ?: getStringSafe("e_note")
    info.originalFromAmount = getLongSafe("original_from_amount", getLongSafe("e_original_from_amount", 0L))
    info.fromAmount = getLongSafe("from_amount", getLongSafe("e_from_amount", 0L))
    info.toAmount = getLongSafe("to_amount", getLongSafe("e_to_amount", 0L))
    info.isTemplate = getIntSafe("is_template", getIntSafe("e_is_template", 0))
    info.templateName = getStringSafe("template_name") ?: getStringSafe("e_template_name")
    info.recurrence = getStringSafe("recurrence") ?: getStringSafe("e_recurrence")
    info.notificationOptions = getStringSafe("notification_options") ?: getStringSafe("e_notification_options")
    val statusStr = getStringSafe("status") ?: getStringSafe("e_status")
    info.status = if (statusStr != null) {
        try { TransactionStatus.valueOf(statusStr) } catch (e: Exception) { TransactionStatus.UR }
    } else {
        TransactionStatus.UR
    }
    info.attachedPicture = getStringSafe("attached_picture") ?: getStringSafe("e_attached_picture")
    info.isCCardPayment = getIntSafe("is_ccard_payment", getIntSafe("e_is_ccard_payment", 0))
    info.lastRecurrence = getLongSafe("last_recurrence", getLongSafe("e_last_recurrence", 0L))
    info.updatedOn = getLongSafe("updated_on", getLongSafe("e_updated_on", 0L))
    info.remoteKey = getStringSafe("remote_key") ?: getStringSafe("e_remote_key")

    // Fetch mapped entities
    val fromAccountId = getLongSafe("from_account_id", getLongSafe("e_from_account_id", 0L))
    info.fromAccount = if (fromAccountId > 0) em.getAccount(fromAccountId) else null
    val toAccountId = getLongSafe("to_account_id", getLongSafe("e_to_account_id", 0L))
    info.toAccount = if (toAccountId > 0) em.getAccount(toAccountId) else null
    val categoryId = getLongSafe("category_id", getLongSafe("e_category_id", 0L))
    info.category = when (categoryId) {
        0L -> Category.noCategory()
        -1L -> Category.splitCategory()
        else -> if (categoryId > 0) em.getCategory(categoryId) ?: Category.noCategory() else Category.noCategory()
    }
    val projectId = getLongSafe("project_id", getLongSafe("e_project_id", 0L))
    info.project = if (projectId > 0) em.getProject(projectId) else null
    val locationId = getLongSafe("location_id", getLongSafe("e_location_id", 0L))
    info.location = if (locationId > 0) em.getLocation(locationId) else null
    val originalCurrencyId = getLongSafe("original_currency_id", getLongSafe("e_original_currency_id", 0L))
    info.originalCurrency = if (originalCurrencyId > 0) CurrencyCache.getCurrency(em, originalCurrencyId) else null
    val payeeId = getLongSafe("payee_id", getLongSafe("e_payee_id", 0L))
    info.payee = if (payeeId > 0) em.getPayee(payeeId) else null
    
    return info
}

// -------------------------------------------------------------------
// ContentValues conversions
// -------------------------------------------------------------------

fun Account.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("creation_date", creationDate)
    cv.put("last_transaction_date", lastTransactionDate)
    cv.put("currency_id", currency?.id ?: 0L)
    cv.put("type", type)
    cv.put("card_issuer", cardIssuer)
    cv.put("issuer", issuer)
    cv.put("number", number)
    cv.put("total_amount", totalAmount)
    cv.put("total_limit", limitAmount)
    cv.put("sort_order", sortOrder)
    cv.put("is_include_into_totals", if (isIncludeIntoTotals) 1 else 0)
    cv.put("last_account_id", lastAccountId)
    cv.put("last_category_id", lastCategoryId)
    cv.put("closing_day", closingDay)
    cv.put("payment_day", paymentDay)
    cv.put("note", note)
    cv.put("is_active", if (isActive) 1 else 0)
    return cv
}

fun Payee.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("last_category_id", lastCategoryId)
    cv.put("sort_order", sortOrder)
    cv.put("is_active", if (isActive) 1 else 0)
    return cv
}

fun Project.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("sort_order", sortOrder)
    cv.put("is_active", if (isActive) 1 else 0)
    return cv
}

fun MyLocation.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("name", title)
    cv.put("provider", provider)
    cv.put("accuracy", accuracy)
    cv.put("longitude", longitude)
    cv.put("latitude", latitude)
    cv.put("resolved_address", resolvedAddress)
    cv.put("datetime", dateTime)
    cv.put("count", count)
    cv.put("sort_order", sortOrder)
    cv.put("is_active", if (isActive) 1 else 0)
    return cv
}

fun Budget.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("category_id", categories)
    cv.put("project_id", projects)
    cv.put("currency_id", currencyId)
    cv.put("budget_currency_id", currency?.id ?: 0L)
    cv.put("budget_account_id", account?.id ?: 0L)
    cv.put("amount", amount)
    cv.put("include_subcategories", if (includeSubcategories) 1 else 0)
    cv.put("expanded", if (expanded) 1 else 0)
    cv.put("include_credit", if (includeCredit) 1 else 0)
    cv.put("start_date", startDate)
    cv.put("end_date", endDate)
    cv.put("recur", recur)
    cv.put("recur_num", recurNum)
    cv.put("is_current", if (isCurrent) 1 else 0)
    cv.put("parent_budget_id", parentBudgetId)
    cv.put("remote_key", remoteKey)
    cv.put("sort_order", sortOrder)
    return cv
}

fun Currency.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("name", name)
    cv.put("symbol", symbol)
    cv.put("symbol_format", symbolFormat.name)
    cv.put("is_default", if (isDefault) 1 else 0)
    cv.put("decimals", decimals)
    cv.put("decimal_separator", decimalSeparator)
    cv.put("group_separator", groupSeparator)
    cv.put("sort_order", sortOrder)
    cv.put("is_active", if (isActive) 1 else 0)
    return cv
}

fun SmsTemplate.toValues(): ContentValues {
    val cv = ContentValues()
    cv.put("title", title)
    cv.put("template", template)
    cv.put("category_id", categoryId)
    cv.put("account_id", accountId)
    cv.put("is_income", if (isIncome) 1 else 0)
    cv.put("sort_order", sortOrder)
    cv.put("is_active", if (isActive) 1 else 0)
    return cv
}

// -------------------------------------------------------------------
// SQL executions via Extensions (delegated from DatabaseAdapter/MyEntityManager)
// -------------------------------------------------------------------

fun MyEntityManager.getTableName(clazz: Class<*>): String? {
    return when (clazz) {
        Account::class.java -> DatabaseHelper.ACCOUNT_TABLE
        Payee::class.java -> DatabaseHelper.PAYEE_TABLE
        Project::class.java -> DatabaseHelper.PROJECT_TABLE
        MyLocation::class.java -> DatabaseHelper.LOCATIONS_TABLE
        Category::class.java -> DatabaseHelper.CATEGORY_TABLE
        Currency::class.java -> DatabaseHelper.CURRENCY_TABLE
        SmsTemplate::class.java -> DatabaseHelper.SMS_TEMPLATES_TABLE
        Budget::class.java -> DatabaseHelper.BUDGET_TABLE
        Transaction::class.java -> DatabaseHelper.TRANSACTION_TABLE
        else -> null
    }
}

fun MyEntityManager.queryEntitiesCursor(
    tableName: String,
    include0: Boolean,
    onlyActive: Boolean,
    filter: String?,
    sort: Array<out Sort>?
): Cursor {
    val selection = StringBuilder()
    val selectionArgs = mutableListOf<String>()
    
    selection.append(if (include0) "_id >= 0" else "_id > 0")
    
    if (onlyActive) {
        selection.append(" AND is_active = 1")
    }
    
    if (!filter.isNullOrBlank()) {
        val titleLike = filter.replace(" ", "%")
        selection.append(" AND (title LIKE ? OR title LIKE ?)")
        selectionArgs.add("%$titleLike%")
        selectionArgs.add("%${StringUtil.capitalize(titleLike)}%")
    }
    val order = sort?.takeIf { it.isNotEmpty() }?.joinToString(", ") { s ->
        val col = when (s.field) {
            "sortOrder", "sort_order" -> "sort_order"
            "totalAmount", "total_amount" -> "total_amount"
            "creationDate", "creation_date" -> "creation_date"
            "lastTransactionDate", "last_transaction_date" -> "last_transaction_date"
            else -> s.field
        }
        "$col ${if (s.asc) "ASC" else "DESC"}"
    } ?: "title ASC"
    
    return db().query(
        tableName,
        null,
        selection.toString(),
        if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
        null,
        null,
        order
    )
}

// Lists

fun MyEntityManager.getAccountsList(include0: Boolean, onlyActive: Boolean, filter: String?, sort: Array<out Sort>?): ArrayList<Account> {
    val list = ArrayList<Account>()
    var e0: Account? = null
    queryEntitiesCursor(DatabaseHelper.ACCOUNT_TABLE, include0, onlyActive, filter, sort).use { c ->
        while (c.moveToNext()) {
            val e = c.toAccount(this)
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
    return list
}

fun MyEntityManager.getPayeesList(include0: Boolean, onlyActive: Boolean, filter: String?, sort: Array<out Sort>?): ArrayList<Payee> {
    val list = ArrayList<Payee>()
    var e0: Payee? = null
    queryEntitiesCursor(DatabaseHelper.PAYEE_TABLE, include0, onlyActive, filter, sort).use { c ->
        while (c.moveToNext()) {
            val e = c.toPayee()
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
    return list
}

fun MyEntityManager.getProjectsList(include0: Boolean, onlyActive: Boolean, filter: String?, sort: Array<out Sort>?): ArrayList<Project> {
    val list = ArrayList<Project>()
    var e0: Project? = null
    queryEntitiesCursor(DatabaseHelper.PROJECT_TABLE, include0, onlyActive, filter, sort).use { c ->
        while (c.moveToNext()) {
            val e = c.toProject()
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
    return list
}

fun MyEntityManager.getLocationsList(include0: Boolean, onlyActive: Boolean, filter: String?, sort: Array<out Sort>?): ArrayList<MyLocation> {
    val list = ArrayList<MyLocation>()
    var e0: MyLocation? = null
    queryEntitiesCursor(DatabaseHelper.LOCATIONS_TABLE, include0, onlyActive, filter, sort).use { c ->
        while (c.moveToNext()) {
            val e = c.toMyLocation()
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
    return list
}

fun MyEntityManager.getCategoriesList(include0: Boolean, onlyActive: Boolean, filter: String?, sort: Array<out Sort>?): ArrayList<Category> {
    val list = ArrayList<Category>()
    var e0: Category? = null
    queryEntitiesCursor(DatabaseHelper.CATEGORY_TABLE, include0, onlyActive, filter, sort).use { c ->
        while (c.moveToNext()) {
            val e = Category.formCursor(c)
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
    return list
}

fun MyEntityManager.getCurrenciesList(include0: Boolean, onlyActive: Boolean, filter: String?, sort: Array<out Sort>?): ArrayList<Currency> {
    val list = ArrayList<Currency>()
    var e0: Currency? = null
    queryEntitiesCursor(DatabaseHelper.CURRENCY_TABLE, include0, onlyActive, filter, sort).use { c ->
        while (c.moveToNext()) {
            val e = c.toCurrency()
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
    return list
}

// Standard typed gets

@JvmName("getAccount")
fun MyEntityManager.getAccount(id: Long): Account? {
    db().query(DatabaseHelper.ACCOUNT_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toAccount(this)
    }
    return null
}

@JvmName("getPayee")
fun MyEntityManager.getPayee(id: Long): Payee? {
    db().query(DatabaseHelper.PAYEE_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toPayee()
    }
    return null
}

@JvmName("getProject")
fun MyEntityManager.getProject(id: Long): Project? {
    db().query(DatabaseHelper.PROJECT_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toProject()
    }
    return null
}

@JvmName("getLocation")
fun MyEntityManager.getLocation(id: Long): MyLocation? {
    db().query(DatabaseHelper.LOCATIONS_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toMyLocation()
    }
    return null
}

@JvmName("getCategory")
fun MyEntityManager.getCategory(id: Long): Category? {
    db().query(DatabaseHelper.V_CATEGORY, DatabaseHelper.CategoryViewColumns.NORMAL_PROJECTION, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return Category.formCursor(c)
    }
    return null
}

@JvmName("getCurrency")
fun MyEntityManager.getCurrency(id: Long): Currency? {
    db().query(DatabaseHelper.CURRENCY_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toCurrency()
    }
    return null
}

@JvmName("getBudget")
fun MyEntityManager.getBudget(id: Long): Budget? {
    db().query(DatabaseHelper.BUDGET_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toBudget(this)
    }
    return null
}

@JvmName("getSmsTemplate")
fun MyEntityManager.getSmsTemplate(id: Long): SmsTemplate? {
    db().query(DatabaseHelper.SMS_TEMPLATES_TABLE, DatabaseHelper.SmsTemplateColumns.NORMAL_PROJECTION, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return SmsTemplate.fromCursor(c)
    }
    return null
}

@JvmName("getTransaction")
fun MyEntityManager.getTransaction(id: Long): Transaction? {
    db().query(DatabaseHelper.TRANSACTION_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toTransaction()
    }
    return null
}

@JvmName("getTransactionInfo")
fun MyEntityManager.getTransactionInfo(id: Long): TransactionInfo? {
    db().query(DatabaseHelper.TRANSACTION_TABLE, null, "_id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toTransactionInfo(this)
    }
    return null
}

// Saves

fun MyEntityManager.getMaxSortOrder(tableName: String): Long {
    db().rawQuery("SELECT max(sort_order) FROM $tableName", null).use { c ->
        if (c.moveToFirst()) return c.getLong(0)
    }
    return 0L
}

@JvmName("saveAccount")
fun MyEntityManager.saveAccount(account: Account): Long {
    val cv = account.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (account.id > 0) {
        db().update(DatabaseHelper.ACCOUNT_TABLE, cv, "_id=?", arrayOf(account.id.toString()))
        account.id
    } else {
        cv.remove("_id")
        if (account.sortOrder <= 0) {
            account.sortOrder = (getMaxSortOrder(DatabaseHelper.ACCOUNT_TABLE) + 1).toInt()
            cv.put("sort_order", account.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.ACCOUNT_TABLE, null, cv)
        account.id = id
        id
    }
}

@JvmName("savePayee")
fun MyEntityManager.savePayee(payee: Payee): Long {
    val cv = payee.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (payee.id > 0) {
        db().update(DatabaseHelper.PAYEE_TABLE, cv, "_id=?", arrayOf(payee.id.toString()))
        payee.id
    } else {
        cv.remove("_id")
        if (payee.sortOrder <= 0) {
            payee.sortOrder = getMaxSortOrder(DatabaseHelper.PAYEE_TABLE) + 1
            cv.put("sort_order", payee.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.PAYEE_TABLE, null, cv)
        payee.id = id
        id
    }
}

@JvmName("saveProject")
fun MyEntityManager.saveProject(project: Project): Long {
    val cv = project.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (project.id > 0) {
        db().update(DatabaseHelper.PROJECT_TABLE, cv, "_id=?", arrayOf(project.id.toString()))
        project.id
    } else {
        cv.remove("_id")
        if (project.sortOrder <= 0) {
            project.sortOrder = getMaxSortOrder(DatabaseHelper.PROJECT_TABLE) + 1
            cv.put("sort_order", project.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.PROJECT_TABLE, null, cv)
        project.id = id
        id
    }
}

@JvmName("saveLocation")
fun MyEntityManager.saveLocation(location: MyLocation): Long {
    val cv = location.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (location.id > 0) {
        db().update(DatabaseHelper.LOCATIONS_TABLE, cv, "_id=?", arrayOf(location.id.toString()))
        location.id
    } else {
        cv.remove("_id")
        if (location.sortOrder <= 0) {
            location.sortOrder = getMaxSortOrder(DatabaseHelper.LOCATIONS_TABLE) + 1
            cv.put("sort_order", location.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.LOCATIONS_TABLE, null, cv)
        location.id = id
        id
    }
}

@JvmName("saveCategory")
fun MyEntityManager.saveCategory(category: Category): Long {
    val cv = ContentValues().apply {
        put("title", category.title)
        put("left", category.left)
        put("right", category.right)
        put("type", category.type)
        put("last_location_id", category.lastLocationId)
        put("last_project_id", category.lastProjectId)
    }
    return if (category.id > 0) {
        db().update(DatabaseHelper.CATEGORY_TABLE, cv, "_id=?", arrayOf(category.id.toString()))
        category.id
    } else {
        val id = db().insertOrThrow(DatabaseHelper.CATEGORY_TABLE, null, cv)
        category.id = id
        id
    }
}

@JvmName("saveCurrency")
fun MyEntityManager.saveCurrency(currency: Currency): Long {
    val cv = currency.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (currency.id > 0) {
        db().update(DatabaseHelper.CURRENCY_TABLE, cv, "_id=?", arrayOf(currency.id.toString()))
        currency.id
    } else {
        cv.remove("_id")
        if (currency.sortOrder <= 0) {
            currency.sortOrder = getMaxSortOrder(DatabaseHelper.CURRENCY_TABLE) + 1
            cv.put("sort_order", currency.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.CURRENCY_TABLE, null, cv)
        currency.id = id
        id
    }
}

@JvmName("saveBudget")
fun MyEntityManager.saveBudget(budget: Budget): Long {
    val cv = budget.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (budget.id > 0) {
        db().update(DatabaseHelper.BUDGET_TABLE, cv, "_id=?", arrayOf(budget.id.toString()))
        budget.id
    } else {
        cv.remove("_id")
        if (budget.sortOrder <= 0) {
            budget.sortOrder = getMaxSortOrder(DatabaseHelper.BUDGET_TABLE) + 1
            cv.put("sort_order", budget.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.BUDGET_TABLE, null, cv)
        budget.id = id
        id
    }
}

@JvmName("saveSmsTemplate")
fun MyEntityManager.saveSmsTemplate(smsTemplate: SmsTemplate): Long {
    val cv = smsTemplate.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (smsTemplate.id > 0) {
        db().update(DatabaseHelper.SMS_TEMPLATES_TABLE, cv, "_id=?", arrayOf(smsTemplate.id.toString()))
        smsTemplate.id
    } else {
        cv.remove("_id")
        if (smsTemplate.sortOrder <= 0) {
            smsTemplate.sortOrder = getMaxSortOrder(DatabaseHelper.SMS_TEMPLATES_TABLE) + 1
            cv.put("sort_order", smsTemplate.sortOrder)
        }
        val id = db().insertOrThrow(DatabaseHelper.SMS_TEMPLATES_TABLE, null, cv)
        smsTemplate.id = id
        id
    }
}

@JvmName("insertOrUpdate")
fun MyEntityManager.insertOrUpdate(transaction: Transaction): Long {
    val cv = transaction.toValues()
    cv.remove("updated_on")
    cv.put("updated_on", System.currentTimeMillis())
    return if (transaction.id > 0) {
        db().update(DatabaseHelper.TRANSACTION_TABLE, cv, "_id=?", arrayOf(transaction.id.toString()))
        transaction.id
    } else {
        cv.remove("_id")
        val id = db().insertOrThrow(DatabaseHelper.TRANSACTION_TABLE, null, cv)
        transaction.id = id
        id
    }
}

@JvmName("filterActiveEntities")
fun MyEntityManager.filterActiveEntities(clazz: Class<*>, titleLike: String?): Cursor? {
    val tableName = getTableName(clazz) ?: return null
    var sql = "SELECT _id, title as e_title FROM $tableName WHERE is_active=1"
    val args = mutableListOf<String>()
    if (!titleLike.isNullOrBlank()) {
        val cleanLike = titleLike.replace(" ", "%")
        sql += " AND (title LIKE ? OR title LIKE ?)"
        args.add("%$cleanLike%")
        args.add("%${StringUtil.capitalize(cleanLike)}%")
    }
    sql += " ORDER BY title ASC"
    return db().rawQuery(sql, args.toTypedArray())
}

@JvmName("getHomeCurrencyDirect")
fun MyEntityManager.getHomeCurrencyDirect(): Currency {
    db().query(DatabaseHelper.CURRENCY_TABLE, null, "is_default=1", null, null, null, null).use { c ->
        if (c.moveToFirst()) return c.toCurrency()
    }
    return Currency.EMPTY
}

@JvmName("getAllScheduledTransactionsDirect")
fun MyEntityManager.getAllScheduledTransactionsDirect(): ArrayList<TransactionInfo> {
    val list = ArrayList<TransactionInfo>()
    db().query(DatabaseHelper.TRANSACTION_TABLE, null, "is_template=2 AND parent_id=0", null, null, null, null).use { c ->
        while (c.moveToNext()) {
            list.add(c.toTransactionInfo(this))
        }
    }
    return list
}

@JvmName("getSplitsForTransaction")
fun MyEntityManager.getSplitsForTransaction(transactionId: Long): List<Transaction> {
    val list = ArrayList<Transaction>()
    db().query(DatabaseHelper.TRANSACTION_TABLE, null, "parent_id=?", arrayOf(transactionId.toString()), null, null, null).use { c ->
        while (c.moveToNext()) {
            list.add(c.toTransaction())
        }
    }
    return list
}

@JvmName("getSplitsInfoForTransaction")
fun MyEntityManager.getSplitsInfoForTransaction(transactionId: Long): List<TransactionInfo> {
    val list = ArrayList<TransactionInfo>()
    db().query(DatabaseHelper.TRANSACTION_TABLE, null, "parent_id=?", arrayOf(transactionId.toString()), null, null, null).use { c ->
        while (c.moveToNext()) {
            list.add(c.toTransactionInfo(this))
        }
    }
    return list
}

@JvmName("getTransactionsForAccount")
fun MyEntityManager.getTransactionsForAccount(accountId: Long): List<TransactionInfo> {
    val list = ArrayList<TransactionInfo>()
    db().query(DatabaseHelper.TRANSACTION_TABLE, null, "from_account_id=? AND parent_id=0", arrayOf(accountId.toString()), null, null, "datetime DESC").use { c ->
        while (c.moveToNext()) {
            list.add(c.toTransactionInfo(this))
        }
    }
    return list
}

@JvmName("getAttributesForTransaction")
fun MyEntityManager.getAttributesForTransaction(transactionId: Long): List<TransactionAttributeInfo> {
    val list = ArrayList<TransactionAttributeInfo>()
    db().query("V_TRANSACTION_ATTRIBUTES", null, "_id=? AND attribute_id>=0", arrayOf(transactionId.toString()), null, null, "attribute_name ASC").use { c ->
        while (c.moveToNext()) {
            list.add(c.toTransactionAttributeInfo())
        }
    }
    return list
}

@JvmName("getSystemAttributeForTransaction")
fun MyEntityManager.getSystemAttributeForTransaction(sa: SystemAttribute, transactionId: Long): TransactionAttributeInfo? {
    db().query("V_TRANSACTION_ATTRIBUTES", null, "_id=? AND attribute_id=?", arrayOf(transactionId.toString(), sa.id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) return c.toTransactionAttributeInfo()
    }
    return null
}

@JvmName("getAllAccountsCursor")
fun MyEntityManager.getAllAccountsCursor(isActiveOnly: Boolean, includeAccounts: LongArray): Cursor {
    val sortOrder = MyPreferences.getAccountSortOrder(context)
    val selection = StringBuilder()
    val selectionArgs = mutableListOf<String>()
    
    if (isActiveOnly) {
        selection.append("is_active = 1")
        if (includeAccounts.isNotEmpty()) {
            selection.append(" OR _id IN (")
            selection.append(includeAccounts.joinToString(",") { "?" })
            selection.append(")")
            includeAccounts.forEach { selectionArgs.add(it.toString()) }
        }
    }
    
    val orderBy = StringBuilder("is_active DESC, ")
    val colName = when (sortOrder.property) {
        "sortOrder", "sort_order" -> "sort_order"
        "totalAmount", "total_amount" -> "total_amount"
        "creationDate", "creation_date" -> "creation_date"
        "lastTransactionDate", "last_transaction_date" -> "last_transaction_date"
        else -> sortOrder.property
    }
    orderBy.append(colName).append(if (sortOrder.asc) " ASC" else " DESC")
    orderBy.append(", title ASC")
    
    return db().query(
        DatabaseHelper.ACCOUNT_TABLE,
        null,
        if (selection.isNotEmpty()) selection.toString() else null,
        if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
        null,
        null,
        orderBy.toString()
    )
}
