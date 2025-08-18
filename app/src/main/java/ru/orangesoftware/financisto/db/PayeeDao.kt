package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.Payee

@Dao
interface PayeeDao {
    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE}")
    fun getAll(): List<Payee>

    @Insert
    fun insert(payee: Payee): Long

    @Update
    fun update(payee: Payee)

    @Delete
    fun delete(payee: Payee)
}
