package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.CURRENCY_TABLE)
data class CurrencyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = "name") // Assuming 'name' from MyEntityManager.getAllCurrenciesList("name")
    var name: String,

    @ColumnInfo(name = "symbol")
    var symbol: String? = null,

    @ColumnInfo(name = "rate", defaultValue = "1.0")
    var rate: Double = 1.0,

    @ColumnInfo(name = "is_default", defaultValue = "0")
    var isDefault: Boolean = false,

    @ColumnInfo(name = "symbol_format", defaultValue = "") // Defaulting to empty string
    var symbolFormat: String = "",

    @ColumnInfo(name = "iso_code")
    var isoCode: String? = null,

    @ColumnInfo(name = "group_separator")
    var groupSeparator: Char? = null,

    @ColumnInfo(name = "decimal_separator")
    var decimalSeparator: Char? = null
)
