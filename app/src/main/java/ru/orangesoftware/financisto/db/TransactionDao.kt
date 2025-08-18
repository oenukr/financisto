package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.Transaction

@Dao
interface TransactionDao {
    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE}")
    fun getAll(): List<Transaction>

    @Insert
    fun insert(transaction: Transaction): Long

    @Update
    fun update(transaction: Transaction)

    @Delete
    fun delete(transaction: Transaction)
}
