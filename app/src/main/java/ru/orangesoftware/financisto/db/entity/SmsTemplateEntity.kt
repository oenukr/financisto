package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(
    tableName = DatabaseHelper.SMS_TEMPLATES_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_DEFAULT // Or CASCADE / SET_NULL
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_DEFAULT // Or CASCADE / SET_NULL
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["account_id"]),
        Index(value = [DatabaseHelper.SmsTemplateColumns.title.name])
    ]
)
data class SmsTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.SmsTemplateColumns.title.name) // This is likely the SMS number/sender
    var title: String,

    @ColumnInfo(name = DatabaseHelper.SmsTemplateColumns.template.name)
    var template: String? = null, // The regex or pattern

    @ColumnInfo(name = "category_id", defaultValue = "0")
    var categoryId: Long = 0,

    @ColumnInfo(name = "account_id", defaultValue = "0")
    var accountId: Long = 0,

    @ColumnInfo(name = "is_income", defaultValue = "0")
    var isIncome: Boolean = false,

    @ColumnInfo(name = DatabaseHelper.SmsTemplateColumns.sort_order.name, defaultValue = "0")
    var sortOrder: Int = 0
)
