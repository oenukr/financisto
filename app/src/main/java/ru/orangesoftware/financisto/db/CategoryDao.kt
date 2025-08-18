package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.Category

@Dao
interface CategoryDao {
    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE}")
    fun getAll(): List<Category>

    @Insert
    fun insert(category: Category): Long

    @Update
    fun update(category: Category)

    @Delete
    fun delete(category: Category)
}
