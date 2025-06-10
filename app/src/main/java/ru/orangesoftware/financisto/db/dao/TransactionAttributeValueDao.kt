package ru.orangesoftware.financisto.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.db.entity.TransactionAttributeValueEntity

@Dao
interface TransactionAttributeValueDao {

    @Upsert // Handles insert or update based on composite primary key
    suspend fun upsert(transactionAttributeValue: TransactionAttributeValueEntity)

    @Upsert
    suspend fun upsertAll(values: List<TransactionAttributeValueEntity>)

    @Query("""
        DELETE FROM ${DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE}
        WHERE transaction_id = :transactionId AND attribute_id = :attributeId
    """)
    suspend fun delete(transactionId: Long, attributeId: Long): Int

    @Query("DELETE FROM ${DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE} WHERE transaction_id = :transactionId")
    suspend fun deleteByTransactionId(transactionId: Long)

    @Query("DELETE FROM ${DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE} WHERE attribute_id = :attributeId")
    suspend fun deleteByAttributeId(attributeId: Long)

    @Query("""
        SELECT * FROM ${DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE}
        WHERE transaction_id = :transactionId
    """)
    fun getAttributeValuesForTransaction(transactionId: Long): Flow<List<TransactionAttributeValueEntity>>

    // For System Attributes like "Payee", "Location", "Project" which might be stored here or derived.
    // Assuming they are stored with specific attribute IDs.
    // This is a generic getter; specific attribute IDs would be used in practice.
    @Query("""
        SELECT * FROM ${DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE}
        WHERE transaction_id = :transactionId AND attribute_id = :attributeId
        LIMIT 1
    """)
    suspend fun getAttributeValue(transactionId: Long, attributeId: Long): TransactionAttributeValueEntity?
}
