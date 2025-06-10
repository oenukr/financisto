/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 *     Abdsandryk - parameters for bill filtering
 ******************************************************************************/
package ru.orangesoftware.financisto.shared.model

// import static ru.orangesoftware.financisto.db.DatabaseHelper.ACCOUNT_TABLE; // KMP compatible DB access will be different
// import static ru.orangesoftware.orb.EntityManager.DEF_SORT_COL; // KMP compatible DB access will be different

// import javax.persistence.Column; // JPA
// import javax.persistence.Entity; // JPA
// import javax.persistence.JoinColumn; // JPA
// import javax.persistence.Table; // JPA
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index

// TODO: Define actual table name for accounts
@Entity(
    tableName = "accounts", // ACCOUNT_TABLE replacement
    foreignKeys = [
        ForeignKey(
            entity = Currency::class,
            parentColumns = ["id"],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.RESTRICT // Or desired action
        )
    ],
    indices = [Index(value = ["currency_id"])]
)
open class Account : MyEntity() { // Made open for potential extension

    @ColumnInfo(name = "creation_date")
    var creationDate: Long = 0L // System.currentTimeMillis() should be set at time of creation, not as default here

    @ColumnInfo(name = "last_transaction_date")
    var lastTransactionDate: Long = 0L // Similar to creationDate

    // This currency_id is the foreign key column. The 'currency' object itself is for runtime use.
    @ColumnInfo(name = "currency_id")
    var currencyId: Long? = null // Foreign key to Currency table

    @Ignore // The actual Currency object is not stored directly in this table's column
    var currency: Currency? = null // Changed to nullable, assuming it can be null

    // For AccountType (enum), Room will store it as String by default.
    @ColumnInfo(name = "type")
    var type: String = AccountType.CASH.name // Assuming AccountType.CASH.name is accessible and correct

    @ColumnInfo(name = "card_issuer")
    var cardIssuer: String? = null // Changed to nullable

    @ColumnInfo(name = "issuer")
    var issuer: String? = null // Changed to nullable

    @ColumnInfo(name = "number")
    var number: String? = null // Changed to nullable

    @ColumnInfo(name = "total_amount")
    var totalAmount: Long = 0L

    @ColumnInfo(name = "limit_amount") // Renamed from limitAmount to match column name
    var limitAmount: Long = 0L

    @ColumnInfo(name = "sort_order") // DEF_SORT_COL replacement
    var sortOrder: Int = 0

    @ColumnInfo(name = "is_include_into_totals")
    var isIncludeIntoTotals: Boolean = true

    @ColumnInfo(name = "last_account_id")
    var lastAccountId: Long = 0L

    @ColumnInfo(name = "last_category_id")
    var lastCategoryId: Long = 0L

    @ColumnInfo(name = "closing_day")
    var closingDay: Int = 0

    @ColumnInfo(name = "payment_day")
    var paymentDay: Int = 0

    @ColumnInfo(name = "note")
    var note: String? = null // Changed to nullable

    fun shouldIncludeIntoTotals(): Boolean {
        return isActive && isIncludeIntoTotals // isActive is inherited from MyEntity
    }
}
