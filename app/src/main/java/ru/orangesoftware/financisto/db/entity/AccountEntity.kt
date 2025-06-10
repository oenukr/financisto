package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.ACCOUNT_TABLE)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.TITLE)
    var title: String? = null,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.CREATION_DATE)
    var creationDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = DatabaseHelper.AccountColumns.CURRENCY_ID)
    var currencyId: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.TYPE)
    var type: String? = null,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.ISSUER)
    var issuer: String? = null,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.NUMBER)
    var number: String? = null,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.TOTAL_AMOUNT)
    var totalAmount: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.SORT_ORDER, defaultValue = "0") // Existing schema might not have a default in all versions
    var sortOrder: Int = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.LAST_CATEGORY_ID)
    var lastCategoryId: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.LAST_ACCOUNT_ID)
    var lastAccountId: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.CLOSING_DAY, defaultValue = "0") // Assuming 0 if not set
    var closingDay: Int = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.PAYMENT_DAY, defaultValue = "0") // Assuming 0 if not set
    var paymentDay: Int = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.IS_INCLUDE_INTO_TOTALS, defaultValue = "1")
    var isIncludeIntoTotals: Boolean = true,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.IS_ACTIVE, defaultValue = "1")
    var isActive: Boolean = true,

    @ColumnInfo(name = "limit_amount", defaultValue = "0")
    var limitAmount: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AccountColumns.LAST_TRANSACTION_DATE)
    var lastTransactionDate: Long = 0,

    @ColumnInfo(name = "updated_on", defaultValue = "0")
    var updatedOn: Long = 0, // Added updatedOn

    @ColumnInfo(name = "note") // Added note
    var note: String? = null
)
