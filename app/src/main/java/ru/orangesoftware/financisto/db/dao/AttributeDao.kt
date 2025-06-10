package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.AttributeEntity

@Dao
interface AttributeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attribute: AttributeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attributes: List<AttributeEntity>)

    @Update
    suspend fun update(attribute: AttributeEntity)

    @Query("DELETE FROM ${DatabaseHelper.ATTRIBUTES_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.ATTRIBUTES_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): AttributeEntity?

    @Query("SELECT * FROM ${DatabaseHelper.ATTRIBUTES_TABLE} ORDER BY title ASC")
    fun getAll(): Flow<List<AttributeEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.ATTRIBUTES_TABLE} WHERE _id IN (:ids)")
    fun getByIds(ids: List<Long>): Flow<List<AttributeEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.ATTRIBUTES_TABLE} WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): AttributeEntity?

    @Query("SELECT * FROM ${DatabaseHelper.ATTRIBUTES_TABLE} WHERE type = :type ORDER BY title ASC")
    fun getByType(type: Int): Flow<List<AttributeEntity>>
}
