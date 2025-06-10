package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.ProjectEntity

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM ${DatabaseHelper.PROJECT_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE} ORDER BY title ASC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE} WHERE is_active = 1 ORDER BY title ASC")
    fun getAllActive(): Flow<List<ProjectEntity>>

    // Mimics MyEntityManager.filterActiveEntities and queryEntities
    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE} WHERE title LIKE '%' || :query || '%' AND is_active = 1 ORDER BY title ASC")
    fun filterActiveByTitle(query: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE} WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun queryByTitle(query: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE} WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): ProjectEntity?
}
