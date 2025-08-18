package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.TransactionAttributeInfo

@Dao
interface TransactionAttributeInfoDao {
    @Query("SELECT * FROM ${DatabaseHelper.V_TRANSACTION_ATTRIBUTES}")
    fun getAll(): List<TransactionAttributeInfo>

    @Insert
    fun insert(transactionAttributeInfo: TransactionAttributeInfo): Long

    @Update
    fun update(transactionAttributeInfo: TransactionAttributeInfo)

    @Delete
    fun delete(transactionAttributeInfo: TransactionAttributeInfo)
}
