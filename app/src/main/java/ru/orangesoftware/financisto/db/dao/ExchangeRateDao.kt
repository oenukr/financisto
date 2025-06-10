package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.ExchangeRateEntity

@Dao
interface ExchangeRateDao {

    @Upsert // Handles both insert and update based on PrimaryKey
    suspend fun upsert(exchangeRate: ExchangeRateEntity)

    @Upsert
    suspend fun upsertAll(exchangeRates: List<ExchangeRateEntity>)

    @Query("""
        DELETE FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE}
        WHERE from_currency_id = :fromCurrencyId
        AND to_currency_id = :toCurrencyId
        AND rate_date = :rateDate
    """)
    suspend fun delete(fromCurrencyId: Long, toCurrencyId: Long, rateDate: Long): Int

    // DatabaseAdapter.findRate(long fromCurrencyId, long toCurrencyId, long date)
    // Finds the rate for a specific date or the closest earlier date.
    @Query("""
        SELECT * FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE}
        WHERE from_currency_id = :fromCurrencyId
        AND to_currency_id = :toCurrencyId
        AND rate_date <= :date
        ORDER BY rate_date DESC
        LIMIT 1
    """)
    suspend fun findRateOnOrBeforeDate(fromCurrencyId: Long, toCurrencyId: Long, date: Long): ExchangeRateEntity?

    // DatabaseAdapter.findRates(long fromCurrencyId, long toCurrencyId)
    // Gets all rates for a specific currency pair, ordered by date.
    @Query("""
        SELECT * FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE}
        WHERE from_currency_id = :fromCurrencyId
        AND to_currency_id = :toCurrencyId
        ORDER BY rate_date DESC
    """)
    fun findRatesForPair(fromCurrencyId: Long, toCurrencyId: Long): Flow<List<ExchangeRateEntity>>

    // DatabaseAdapter.getLatestRates(long date)
    // Gets the latest rate for each currency pair on or before the given date.
    // This is a more complex query for Room, often handled by getting all relevant rates and processing in code,
    // or by using a more specific query if pairs are known.
    // A simplified version: get all rates on or before a date, processing can be done in repository.
    @Query("""
        SELECT * FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE}
        WHERE rate_date = (
            SELECT MAX(rate_date)
            FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE} AS er_inner
            WHERE er_inner.from_currency_id = ${DatabaseHelper.EXCHANGE_RATES_TABLE}.from_currency_id
            AND er_inner.to_currency_id = ${DatabaseHelper.EXCHANGE_RATES_TABLE}.to_currency_id
            AND er_inner.rate_date <= :date
        )
    """)
    fun getLatestRatesOnOrBeforeDate(date: Long): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE} WHERE from_currency_id = :currencyId OR to_currency_id = :currencyId ORDER BY rate_date DESC")
    fun getAllRatesForCurrency(currencyId: Long): Flow<List<ExchangeRateEntity>>

    @Query("DELETE FROM ${DatabaseHelper.EXCHANGE_RATES_TABLE} WHERE from_currency_id = :currencyId OR to_currency_id = :currencyId")
    suspend fun deleteAllForCurrency(currencyId: Long)
}
