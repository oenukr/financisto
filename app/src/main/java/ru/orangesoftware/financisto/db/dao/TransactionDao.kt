package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.TransactionEntity

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity): Int

    @Query("DELETE FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} ORDER BY datetime DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    // For MyEntityManager.getTransactions(long accountId)
    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE from_account_id = :accountId OR to_account_id = :accountId ORDER BY datetime DESC")
    fun getTransactionsForAccount(accountId: Long): Flow<List<TransactionEntity>>

    // For MyEntityManager.getAllScheduledTransactions() - isTemplate = 2 for scheduled
    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE is_template = 2 ORDER BY datetime ASC")
    fun getScheduledTransactions(): Flow<List<TransactionEntity>>

    // For MyEntityManager.getTemplates() - isTemplate = 1 for templates
    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE is_template = 1 ORDER BY template_name ASC")
    fun getTemplates(): Flow<List<TransactionEntity>>

    // For MyEntityManager.getSplitTransactions(long parentId)
    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE parent_id = :parentId ORDER BY _id ASC")
    fun getSplitTransactions(parentId: Long): Flow<List<TransactionEntity>>

    // For MyEntityManager.getTransactions(SupportSQLiteQuery query) - Blotter View / Advanced Search
    // This allows for dynamic queries from the Repository/ViewModel.
    // Note: @RawQuery is powerful but less type-safe. Ensure query construction is secure.
    @androidx.room.RawQuery(observedEntities = [TransactionEntity::class])
    fun getTransactionsRaw(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>

    // For MyEntityManager.deleteTransactions(String selection, String[] selectionArgs)
    // This is complex due to dynamic WHERE clauses. A more specific delete might be safer,
    // or pass a SupportSQLiteQuery to a method that executes a DELETE.
    // For now, providing deleteById and a method to delete transactions of a specific account.
    @Query("DELETE FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE from_account_id = :accountId OR to_account_id = :accountId")
    suspend fun deleteTransactionsForAccount(accountId: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE from_account_id = :accountId AND status = :status ORDER BY datetime DESC")
    fun getTransactionsByAccountAndStatus(accountId: Long, status: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.TRANSACTION_TABLE} WHERE original_currency_id = :currencyId")
    fun getTransactionsByCurrency(currencyId: Long): Flow<List<TransactionEntity>>

    @Query("UPDATE ${DatabaseHelper.TRANSACTION_TABLE} SET status = :newStatus WHERE _id = :transactionId")
    suspend fun updateStatus(transactionId: Long, newStatus: String)

    @Query("UPDATE ${DatabaseHelper.TRANSACTION_TABLE} SET from_account_id = 0 WHERE from_account_id = :accountId")
    suspend fun clearFromAccountId(accountId: Long)

    @Query("UPDATE ${DatabaseHelper.TRANSACTION_TABLE} SET to_account_id = 0 WHERE to_account_id = :accountId")
    suspend fun clearToAccountId(accountId: Long)

    @Query("UPDATE ${DatabaseHelper.TRANSACTION_TABLE} SET category_id = 0 WHERE category_id = :categoryId")
    suspend fun clearCategoryId(categoryId: Long)
}
