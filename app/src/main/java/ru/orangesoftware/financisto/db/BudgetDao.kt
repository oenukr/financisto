package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.Budget

@Dao
interface BudgetDao {
    @Query("SELECT * FROM ${DatabaseHelper.BUDGET_TABLE}")
    fun getAll(): List<Budget>

    @Insert
    fun insert(budget: Budget): Long

    @Update
    fun update(budget: Budget)

    @Delete
    fun delete(budget: Budget)
}
