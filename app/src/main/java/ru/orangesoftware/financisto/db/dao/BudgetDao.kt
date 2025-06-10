package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.BudgetEntity

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM ${DatabaseHelper.BUDGET_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.BUDGET_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Query("SELECT * FROM ${DatabaseHelper.BUDGET_TABLE} ORDER BY start_date DESC")
    fun getAll(): Flow<List<BudgetEntity>>

    // MyEntityManager.getAllBudgets(boolean activeOnly, String selection, String[] selectionArgs, String sortOrder)
    // This is a simplified version. More complex filtering might be needed in Repository.
    // This query gets budgets that are active within the given date range or overlap with it.
    @Query("""
        SELECT * FROM ${DatabaseHelper.BUDGET_TABLE}
        WHERE (
            (start_date <= :endDate AND end_date >= :startDate) OR
            (recur IS NOT NULL AND recur != "")
        )
        ORDER BY start_date DESC
    """)
    fun getBudgetsInRangeOrRecurring(startDate: Long, endDate: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.BUDGET_TABLE} WHERE parent_budget_id = :parentId")
    fun getByParentId(parentId: Long): Flow<List<BudgetEntity>>

    @Query("DELETE FROM ${DatabaseHelper.BUDGET_TABLE} WHERE parent_budget_id = :parentId")
    suspend fun deleteByParentId(parentId: Long): Int

    // For updating child budget references if a parent is changed/deleted.
    // This would typically be part of a transaction in the repository.
    @Query("UPDATE ${DatabaseHelper.BUDGET_TABLE} SET parent_budget_id = :newParentId WHERE parent_budget_id = :oldParentId")
    suspend fun updateParentBudgetId(oldParentId: Long, newParentId: Long)

    @Query("DELETE FROM ${DatabaseHelper.BUDGET_TABLE} WHERE category_id = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)

    @Query("DELETE FROM ${DatabaseHelper.BUDGET_TABLE} WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
}
