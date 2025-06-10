package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.LocationEntity

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Query("DELETE FROM ${DatabaseHelper.LOCATIONS_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE} ORDER BY title ASC")
    fun getAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE} WHERE is_active = 1 ORDER BY title ASC")
    fun getAllActive(): Flow<List<LocationEntity>>

    // Mimics MyEntityManager.filterActiveEntities and queryEntities
    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE} WHERE title LIKE '%' || :query || '%' AND is_active = 1 ORDER BY title ASC")
    fun filterActiveByTitle(query: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE} WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun queryByTitle(query: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE} WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): LocationEntity?

    @Query("UPDATE ${DatabaseHelper.LOCATIONS_TABLE} SET count = count + 1 WHERE _id = :id")
    suspend fun incrementCount(id: Long)
}
