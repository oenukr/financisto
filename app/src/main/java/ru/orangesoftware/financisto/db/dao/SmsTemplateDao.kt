package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.SmsTemplateEntity

@Dao
interface SmsTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(smsTemplate: SmsTemplateEntity): Long

    @Update
    suspend fun update(smsTemplate: SmsTemplateEntity)

    @Query("DELETE FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} WHERE _id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} WHERE _id = :id")
    suspend fun getById(id: Long): SmsTemplateEntity?

    @Query("SELECT * FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} ORDER BY sort_order ASC, title ASC")
    fun getAll(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} WHERE category_id = :categoryId ORDER BY sort_order ASC")
    fun getByCategoryId(categoryId: Long): Flow<List<SmsTemplateEntity>>

    // title column in SmsTemplateEntity is likely the sender/number
    @Query("SELECT * FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} WHERE title = :title ORDER BY sort_order ASC")
    fun getByTitle(title: String): Flow<List<SmsTemplateEntity>>

    @Query("SELECT DISTINCT title FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} ORDER BY title ASC")
    fun getAllDistinctTitles(): Flow<List<String>>

    @Query("DELETE FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} WHERE category_id = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)

    @Query("DELETE FROM ${DatabaseHelper.SMS_TEMPLATES_TABLE} WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
}
