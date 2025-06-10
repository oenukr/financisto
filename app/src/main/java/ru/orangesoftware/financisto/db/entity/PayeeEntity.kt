package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.PAYEE_TABLE)
data class PayeeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.EntityColumns.TITLE) // Reusing EntityColumns
    var title: String,

    @ColumnInfo(name = "last_category_id", defaultValue = "0") // From DatabaseAdapter.PAYEE_LAST_CATEGORY_UPDATE
    var lastCategoryId: Long = 0,

    @ColumnInfo(name = "is_active", defaultValue = "1") // Common pattern from MyEntityManager
    var isActive: Boolean = true
)
