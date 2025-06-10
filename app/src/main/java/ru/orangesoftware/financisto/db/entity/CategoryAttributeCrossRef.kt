package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(
    tableName = DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE,
    primaryKeys = ["category_id", "attribute_id"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["_id"],
            childColumns = ["category_id"],
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
        Index(value = ["category_id"]),
        Index(value = ["attribute_id"])
    ]
)
data class CategoryAttributeCrossRef(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "attribute_id")
    val attributeId: Long
)
