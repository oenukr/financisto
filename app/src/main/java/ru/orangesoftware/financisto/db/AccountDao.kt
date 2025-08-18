package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import ru.orangesoftware.financisto.db.DatabaseHelper.ACCOUNT_TABLE
import ru.orangesoftware.financisto.model.Account

@Dao
interface AccountDao {
    @Query("SELECT * FROM $ACCOUNT_TABLE")
    fun getAll(): List<Account>

    @Query("SELECT * FROM $ACCOUNT_TABLE WHERE _id = :id")
    fun get(id: Long): Account?

    @Insert
    fun insert(account: Account): Long

    @Update
    fun update(account: Account)

    @Delete
    fun delete(account: Account)

    @Query("SELECT * FROM $ACCOUNT_TABLE WHERE number LIKE '%' || :numberEnding")
    fun getAccountByNumber(numberEnding: String): List<Account>

    @RawQuery
    fun getAccounts(query: SupportSQLiteQuery): List<Account>
}
