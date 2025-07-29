package ru.orangesoftware.financisto.repository.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.orb.EntityManager

@Entity(tableName = DatabaseHelper.ACCOUNT_TABLE)
data class Account(
    @ColumnInfo(name = "creation_date") val creationDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_transaction_date") val lastTransactionDate: Long = System.currentTimeMillis(),
    // @JoinColumn(name = "currency_id")
    @ForeignKey(
        entity = Currency::class,
        parentColumns = [EntityManager.DEF_ID_COL],
        childColumns = ["currency_id"]
    )
    @ColumnInfo(name = "currency_id") val currency: Currency? = null,
    @ColumnInfo(name = "type") val type: String = AccountType.CASH.name,
    @ColumnInfo(name = "card_issuer") val cardIssuer: String? = null,
    @ColumnInfo(name = "issuer") val issuer: String? = null,
    @ColumnInfo(name = "number") val number: String? = null,
    @ColumnInfo(name = "total_amount") val totalAmount: Long = 0,
    @ColumnInfo(name = "total_limit") val limitAmount: Long = 0,
    @ColumnInfo(name = EntityManager.DEF_SORT_COL) val sortOrder: Int = 0,
    @ColumnInfo(name = "is_include_into_totals") val isIncludeIntoTotals: Boolean = true,
    @ColumnInfo(name = "last_account_id") val lastAccountId: Long = 0,
    @ColumnInfo(name = "last_category_id") val lastCategoryId: Long = 0,
    @ColumnInfo(name = "closing_day") val closingDay: Int = 0,
    @ColumnInfo(name = "payment_day") val paymentDay: Int = 0,
    @ColumnInfo(name = "note") val note: String? = null,
) : MyEntity() {
    fun shouldIncludeIntoTotals(): Boolean {
        return isActive && isIncludeIntoTotals
    }
}
