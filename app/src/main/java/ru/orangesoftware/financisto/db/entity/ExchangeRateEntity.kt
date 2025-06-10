package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(
    tableName = DatabaseHelper.EXCHANGE_RATES_TABLE,
    primaryKeys = ["from_currency_id", "to_currency_id", "rate_date"],
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["_id"],
            childColumns = ["from_currency_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["_id"],
            childColumns = ["to_currency_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["from_currency_id"]),
        Index(value = ["to_currency_id"]),
        Index(value = ["rate_date"])
    ]
)
data class ExchangeRateEntity(
    @ColumnInfo(name = "from_currency_id")
    var fromCurrencyId: Long,

    @ColumnInfo(name = "to_currency_id")
    var toCurrencyId: Long,

    @ColumnInfo(name = "rate_date")
    var rateDate: Long, // Stored as Long (timestamp)

    @ColumnInfo(name = "rate")
    var rate: Double
)
