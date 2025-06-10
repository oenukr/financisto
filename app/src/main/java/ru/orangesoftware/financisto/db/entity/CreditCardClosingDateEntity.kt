package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(
    tableName = DatabaseHelper.CCARD_CLOSING_DATE_TABLE,
    primaryKeys = ["account_id", "period"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
            )
    ]
)
data class CreditCardClosingDateEntity(
    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "period") // MMYYYY format, stored as Int or String. Original uses Int.
    val period: Int,

    @ColumnInfo(name = "closing_day")
    val closingDay: Int
)
