package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM ${DatabaseHelper.CATEGORY_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE} ORDER BY `left` ASC")
    fun getAllSortedByLeft(): Flow<List<CategoryEntity>>

    // Get categories by type (e.g., income, expense)
    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE} WHERE type = :type ORDER BY `left` ASC")
    fun getByTypeSortedByLeft(type: Int): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE} WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): CategoryEntity?

    // This query is based on MyEntityManager.getCategory() which seems to fetch a root category of a certain type.
    // The original query has `parent_id = 0`, which is not directly applicable with nested sets (left/right).
    // A root category in nested set has `left = 1`.
    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE} WHERE `left` = 1 AND type = :type LIMIT 1")
    suspend fun getRootCategoryByType(type: Int): CategoryEntity?

    // Query to get children of a category (not including the category itself)
    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_TABLE} WHERE `left` > :left AND `right` < :right AND type = :type ORDER BY `left` ASC")
    fun getChildren(left: Int, right: Int, type: Int): Flow<List<CategoryEntity>>

    @Query("UPDATE ${DatabaseHelper.CATEGORY_TABLE} SET last_location_id = :locationId WHERE _id = :categoryId")
    suspend fun updateLastLocationId(categoryId: Long, locationId: Long)

    @Query("UPDATE ${DatabaseHelper.CATEGORY_TABLE} SET last_project_id = :projectId WHERE _id = :categoryId")
    suspend fun updateLastProjectId(categoryId: Long, projectId: Long)

    // Note: Operations like moving nodes or re-calculating left/right for nested sets
    // are complex and typically handled in Repository/UseCase layer with multiple DB operations in a transaction.
    // DAO methods here are for direct data access.
}
