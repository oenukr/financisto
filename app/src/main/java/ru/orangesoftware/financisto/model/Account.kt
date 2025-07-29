package ru.orangesoftware.financisto.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.orb.EntityManager

@Entity(
    tableName = DatabaseHelper.ACCOUNT_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = Currency::class,
            parentColumns = [EntityManager.DEF_ID_COL],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.NO_ACTION
        ),
    ]
)
data class Account(
    @ColumnInfo(name = "creation_date") var creationDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_transaction_date") val lastTransactionDate: Long = System.currentTimeMillis(),
    // @JoinColumn(name = "currency_id")
//    @ForeignKey(
//        entity = Currency::class,
//        parentColumns = [EntityManager.DEF_ID_COL],
//        childColumns = ["currency_id"]
//    )
    @ColumnInfo(name = "currency_id") var currency: Currency? = null,
    @ColumnInfo(name = "type") var type: String = AccountType.CASH.name,
    @ColumnInfo(name = "card_issuer") var cardIssuer: String? = null,
    @ColumnInfo(name = "issuer") var issuer: String? = null,
    @ColumnInfo(name = "number") var number: String? = null,
    @ColumnInfo(name = "total_amount") var totalAmount: Long = 0,
    @ColumnInfo(name = "total_limit") var limitAmount: Long = 0,
    @ColumnInfo(name = EntityManager.DEF_SORT_COL) var sortOrder: Int = 0,
    @ColumnInfo(name = "is_include_into_totals") var isIncludeIntoTotals: Boolean = true,
    @ColumnInfo(name = "last_account_id") val lastAccountId: Long = 0,
    @ColumnInfo(name = "last_category_id") val lastCategoryId: Long = 0,
    @ColumnInfo(name = "closing_day") var closingDay: Int = 0,
    @ColumnInfo(name = "payment_day") var paymentDay: Int = 0,
    @ColumnInfo(name = "note") var note: String? = null,
) : MyEntity() {
    fun shouldIncludeIntoTotals(): Boolean {
        return isActive && isIncludeIntoTotals
    }
}
