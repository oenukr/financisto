package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.AttributeEntity
import ru.orangesoftware.financisto.db.entity.CategoryAttributeCrossRef
import ru.orangesoftware.financisto.db.entity.CategoryEntity

@Dao
interface CategoryAttributeCrossRefDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: CategoryAttributeCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(crossRefs: List<CategoryAttributeCrossRef>)

    @Query("""
        DELETE FROM ${DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE}
        WHERE category_id = :categoryId AND attribute_id = :attributeId
    """)
    suspend fun delete(categoryId: Long, attributeId: Long): Int

    @Query("DELETE FROM ${DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE} WHERE category_id = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)

    @Query("DELETE FROM ${DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE} WHERE attribute_id = :attributeId")
    suspend fun deleteByAttributeId(attributeId: Long)

    @Query("""
        SELECT a.* FROM ${DatabaseHelper.ATTRIBUTES_TABLE} a
        INNER JOIN ${DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE} ca ON a._id = ca.attribute_id
        WHERE ca.category_id = :categoryId
        ORDER BY a.title ASC
    """)
    fun getAttributesForCategory(categoryId: Long): Flow<List<AttributeEntity>>

    @Query("""
        SELECT c.* FROM ${DatabaseHelper.CATEGORY_TABLE} c
        INNER JOIN ${DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE} ca ON c._id = ca.category_id
        WHERE ca.attribute_id = :attributeId
        ORDER BY c.title ASC
    """)
    fun getCategoriesForAttribute(attributeId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE} WHERE category_id = :categoryId AND attribute_id = :attributeId LIMIT 1")
    suspend fun getCrossRef(categoryId: Long, attributeId: Long): CategoryAttributeCrossRef?
}
