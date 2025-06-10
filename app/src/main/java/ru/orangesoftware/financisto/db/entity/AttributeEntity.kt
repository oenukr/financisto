package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.ATTRIBUTES_TABLE)
data class AttributeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.AttributeColumns.TITLE)
    var title: String,

    @ColumnInfo(name = DatabaseHelper.AttributeColumns.TYPE)
    var type: Int, // Assuming Int type, check Attribute.java if different

    @ColumnInfo(name = DatabaseHelper.AttributeColumns.LIST_VALUES)
    var listValues: String? = null, // Comma-separated string or JSON

    @ColumnInfo(name = DatabaseHelper.AttributeColumns.DEFAULT_VALUE)
    var defaultValue: String? = null
)
