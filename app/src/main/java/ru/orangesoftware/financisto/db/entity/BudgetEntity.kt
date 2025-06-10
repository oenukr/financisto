package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(
    tableName = DatabaseHelper.BUDGET_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE // Or SET_NULL if category_id can be null
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE // Or SET_NULL if account_id can be null
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_DEFAULT // project_id can be 0
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["_id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.SET_DEFAULT // location_id can be 0
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["_id"],
            childColumns = ["currency_id"],
            onDelete = ForeignKey.CASCADE // Must have a currency
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["account_id"]),
        Index(value = ["project_id"]),
        Index(value = ["location_id"]),
        Index(value = ["currency_id"]),
        Index(value = ["start_date", "end_date"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = "title")
    var title: String,

    @ColumnInfo(name = "amount")
    var amount: Long,

    @ColumnInfo(name = "start_date")
    var startDate: Long,

    @ColumnInfo(name = "end_date")
    var endDate: Long,

    @ColumnInfo(name = "recur") // From MyEntityManager.insertBudget -> budget.recur
    var recur: String? = null,

    @ColumnInfo(name = "recur_num", defaultValue = "0") // From MyEntityManager.insertBudget -> budget.recurNum
    var recurNum: Int = 0,

    @ColumnInfo(name = "parent_budget_id", defaultValue = "0") // From MyEntityManager.insertBudget
    var parentBudgetId: Long = 0,

    @ColumnInfo(name = "category_id")
    var categoryId: Long,

    @ColumnInfo(name = "account_id")
    var accountId: Long,

    @ColumnInfo(name = "project_id", defaultValue = "0")
    var projectId: Long = 0,

    @ColumnInfo(name = "location_id", defaultValue = "0")
    var locationId: Long = 0,

    @ColumnInfo(name = "currency_id")
    var currencyId: Long,

    @ColumnInfo(name = "note")
    var note: String? = null,

    // remote_key is present in Budget.java model, but seems to be set to null always in insertBudget.
    // If it's truly unused or only for sync it might be omitted or added if sync logic is in scope.
    // For now, I'll omit it to keep things simpler.
    // @ColumnInfo(name = "remote_key")
    // var remoteKey: String? = null,

    @ColumnInfo(name = "type", defaultValue = "0") // Assuming a type field might exist, like category type
    var type: Int = 0
)
