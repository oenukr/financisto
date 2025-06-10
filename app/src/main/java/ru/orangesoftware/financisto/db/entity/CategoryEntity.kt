package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.CATEGORY_TABLE)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.title.name)
    var title: String,

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.left.name)
    var left: Int,

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.right.name)
    var right: Int,

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.type.name)
    var type: Int, // Assuming type is an Int (e.g., income/expense)

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.last_location_id.name, defaultValue = "0")
    var lastLocationId: Long = 0,

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.last_project_id.name, defaultValue = "0")
    var lastProjectId: Long = 0,

    @ColumnInfo(name = DatabaseHelper.CategoryColumns.sort_order.name, defaultValue = "0")
    var sortOrder: Int = 0
)
