package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.Currency

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE}")
    fun getAll(): List<Currency>

    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE} WHERE _id = :id")
    fun get(id: Long): Currency?

    @Insert
    fun insert(currency: Currency): Long

    @Update
    fun update(currency: Currency)

    @Delete
    fun delete(currency: Currency)

    @Query("UPDATE ${DatabaseHelper.CURRENCY_TABLE} SET is_default = 0")
    fun clearDefaults()
}
