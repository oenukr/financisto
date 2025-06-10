package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.PROJECT_TABLE)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.EntityColumns.TITLE) // Reusing EntityColumns like in MyEntityManager
    var title: String,

    @ColumnInfo(name = "is_active", defaultValue = "1") // Common pattern for "MyEntity" types
    var isActive: Boolean = true
)
