package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.PayeeEntity

@Dao
interface PayeeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payee: PayeeEntity): Long

    @Update
    suspend fun update(payee: PayeeEntity)

    @Query("DELETE FROM ${DatabaseHelper.PAYEE_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): PayeeEntity?

    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE} ORDER BY title ASC")
    fun getAll(): Flow<List<PayeeEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE} WHERE is_active = 1 ORDER BY title ASC")
    fun getAllActive(): Flow<List<PayeeEntity>>

    // Mimics MyEntityManager.filterActiveEntities and queryEntities
    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE} WHERE title LIKE '%' || :query || '%' AND is_active = 1 ORDER BY title ASC")
    fun filterActiveByTitle(query: String): Flow<List<PayeeEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE} WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun queryByTitle(query: String): Flow<List<PayeeEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.PAYEE_TABLE} WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): PayeeEntity?

    @Query("UPDATE ${DatabaseHelper.PAYEE_TABLE} SET last_category_id = :categoryId WHERE _id = :payeeId")
    suspend fun updateLastCategoryId(payeeId: Long, categoryId: Long)
}
