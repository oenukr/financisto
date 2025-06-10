package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.CreditCardClosingDateEntity

@Dao
interface CreditCardClosingDateDao {

    @Upsert // Handles insert or update based on composite primary key (account_id, period)
    suspend fun upsert(closingDate: CreditCardClosingDateEntity)

    @Upsert
    suspend fun upsertAll(closingDates: List<CreditCardClosingDateEntity>)

    @Query("""
        DELETE FROM ${DatabaseHelper.CCARD_CLOSING_DATE_TABLE}
        WHERE account_id = :accountId AND period = :period
    """)
    suspend fun delete(accountId: Long, period: Int): Int

    @Query("DELETE FROM ${DatabaseHelper.CCARD_CLOSING_DATE_TABLE} WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Long)

    @Query("""
        SELECT * FROM ${DatabaseHelper.CCARD_CLOSING_DATE_TABLE}
        WHERE account_id = :accountId AND period = :period
        LIMIT 1
    """)
    suspend fun getByAccountAndPeriod(accountId: Long, period: Int): CreditCardClosingDateEntity?

    @Query("""
        SELECT * FROM ${DatabaseHelper.CCARD_CLOSING_DATE_TABLE}
        WHERE account_id = :accountId
        ORDER BY period DESC
    """)
    fun getForAccount(accountId: Long): Flow<List<CreditCardClosingDateEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.CCARD_CLOSING_DATE_TABLE} ORDER BY period DESC")
    fun getAll(): Flow<List<CreditCardClosingDateEntity>>
}
