package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ru.orangesoftware.financisto.db.DatabaseHelper

// This table stores the actual value of an attribute for a specific transaction.
// It's more than just a cross-ref, it's an association entity.
@Entity(
    tableName = DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE,
    primaryKeys = ["transaction_id", "attribute_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["_id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AttributeEntity::class,
            parentColumns = ["_id"],
            childColumns = ["attribute_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["attribute_id"])
    ]
)
data class TransactionAttributeValueEntity( // Renamed to reflect its purpose
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    @ColumnInfo(name = "attribute_id")
    val attributeId: Long,

    @ColumnInfo(name = DatabaseHelper.TransactionAttributeColumns.VALUE)
    val value: String? // The actual value of the attribute for this transaction
)
