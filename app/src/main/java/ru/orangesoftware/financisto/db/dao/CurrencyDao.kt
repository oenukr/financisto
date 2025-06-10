package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.CurrencyEntity

@Dao
interface CurrencyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(currency: CurrencyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(currencies: List<CurrencyEntity>)

    @Update
    suspend fun update(currency: CurrencyEntity)

    @Query("DELETE FROM ${DatabaseHelper.CURRENCY_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): CurrencyEntity?

    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE} ORDER BY name ASC")
    fun getAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE} WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultCurrency(): CurrencyEntity?

    // MyEntityManager.getAllCurrenciesList(sortBy) - can be handled by sorting the Flow in ViewModel/Repository
    // or by adding specific methods if direct DB sort is frequently needed for different columns.
    // For now, a general getAll sorted by name is provided.

    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE} WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CurrencyEntity?

    // MyEntityManager.saveOrUpdate logic for setting is_default often involves a transaction
    // to clear other defaults. This will be handled in the Repository/UseCase.
    @Query("UPDATE ${DatabaseHelper.CURRENCY_TABLE} SET is_default = 0 WHERE is_default = 1")
    suspend fun clearAllDefaults()

    @Query("SELECT * FROM ${DatabaseHelper.CURRENCY_TABLE}")
    suspend fun getAllSuspendable(): List<CurrencyEntity>
}
