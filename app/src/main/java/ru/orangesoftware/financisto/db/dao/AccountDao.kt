package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.AccountEntity

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM ${DatabaseHelper.ACCOUNT_TABLE} ORDER BY title ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE is_active = 1 ORDER BY title ASC")
    fun getAllActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE number LIKE '%' || :numberEnding || '%'")
    suspend fun getByNumberEnding(numberEnding: String): List<AccountEntity>

    @Query("SELECT * FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE (:isActiveOnly = 0 OR is_active = 1) OR _id IN (:includeAccountIds) ORDER BY is_active DESC, title ASC")
    fun getAccounts(isActiveOnly: Boolean, includeAccountIds: List<Long>): Flow<List<AccountEntity>>

    @Query("UPDATE ${DatabaseHelper.ACCOUNT_TABLE} SET last_category_id = :categoryId WHERE _id = :accountId")
    suspend fun updateLastCategoryId(accountId: Long, categoryId: Long)

    @Query("UPDATE ${DatabaseHelper.ACCOUNT_TABLE} SET last_account_id = :lastAccountId WHERE _id = :accountId")
    suspend fun updateLastAccountId(accountId: Long, lastAccountId: Long)

    @Query("UPDATE ${DatabaseHelper.ACCOUNT_TABLE} SET total_amount = total_amount + :deltaAmount WHERE _id = :accountId")
    suspend fun updateTotalAmount(accountId: Long, deltaAmount: Long)

    @Query("UPDATE ${DatabaseHelper.ACCOUNT_TABLE} SET last_transaction_date = :lastTransactionDate WHERE _id = :accountId")
    suspend fun updateLastTransactionDate(accountId: Long, lastTransactionDate: Long)

    @Query("SELECT DISTINCT currency_id FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE is_include_into_totals = 1 AND is_active = 1")
    suspend fun getDistinctActiveCurrencyIdsInTotals(): List<Long>

    @Query("SELECT * FROM ${DatabaseHelper.ACCOUNT_TABLE} WHERE is_active = 1")
    suspend fun getAllActiveSuspendable(): List<AccountEntity>
}
