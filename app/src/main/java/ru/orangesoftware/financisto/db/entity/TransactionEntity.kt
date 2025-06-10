package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(
    tableName = DatabaseHelper.TRANSACTION_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["_id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.SET_DEFAULT // Or appropriate action
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["_id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.SET_DEFAULT // Or appropriate action
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["_id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_DEFAULT // Or appropriate action
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["_id"],
            childColumns = ["original_currency_id"],
            onDelete = ForeignKey.SET_DEFAULT
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["_id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_DEFAULT
        ),
        ForeignKey(
            entity = PayeeEntity::class,
            parentColumns = ["_id"],
            childColumns = ["payee_id"],
            onDelete = ForeignKey.SET_DEFAULT
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["_id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [
        Index(value = ["from_account_id"]),
        Index(value = ["to_account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["datetime"]),
        Index(value = ["parent_id"]),
        Index(value = ["project_id"]), // Index for future FK
        Index(value = ["payee_id"]),   // Index for future FK
        Index(value = ["location_id"]) // Index for future FK
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = "parent_id", defaultValue = "0")
    var parentId: Long = 0, // For split transactions

    @ColumnInfo(name = "from_account_id")
    var fromAccountId: Long,

    @ColumnInfo(name = "to_account_id", defaultValue = "0")
    var toAccountId: Long = 0, // Can be 0 if not a transfer

    @ColumnInfo(name = "category_id", defaultValue = "0")
    var categoryId: Long = 0,

    @ColumnInfo(name = "project_id", defaultValue = "0")
    var projectId: Long = 0,

    @ColumnInfo(name = "payee_id", defaultValue = "0")
    var payeeId: Long = 0,

    @ColumnInfo(name = "note")
    var note: String? = null,

    @ColumnInfo(name = "from_amount")
    var fromAmount: Long,

    @ColumnInfo(name = "to_amount", defaultValue = "0")
    var toAmount: Long = 0,

    @ColumnInfo(name = "datetime")
    var dateTime: Long,

    @ColumnInfo(name = "original_currency_id", defaultValue = "0")
    var originalCurrencyId: Long = 0,

    @ColumnInfo(name = "original_from_amount", defaultValue = "0")
    var originalFromAmount: Long = 0,

    @ColumnInfo(name = "location_id", defaultValue = "0")
    var locationId: Long = 0,

    @ColumnInfo(name = "provider")
    var provider: String? = null,

    @ColumnInfo(name = "accuracy", defaultValue = "0.0")
    var accuracy: Double = 0.0,

    @ColumnInfo(name = "latitude", defaultValue = "0.0")
    var latitude: Double = 0.0,

    @ColumnInfo(name = "longitude", defaultValue = "0.0")
    var longitude: Double = 0.0,

    @ColumnInfo(name = "is_template", defaultValue = "0") // 0 = normal, 1 = template, 2 = scheduled
    var isTemplate: Int = 0,

    @ColumnInfo(name = "template_name")
    var templateName: String? = null,

    @ColumnInfo(name = "recurrence") // Store as String, actual parsing logic elsewhere
    var recurrence: String? = null,

    @ColumnInfo(name = "notification_options") // Store as String
    var notificationOptions: String? = null,

    @ColumnInfo(name = "status") // e.g., "RS" (Restored Schedule), "CL" (Cleared), "RC" (Reconciled)
    var status: String? = null,

    @ColumnInfo(name = "attached_picture")
    var attachedPicture: String? = null,

    @ColumnInfo(name = "is_ccard_payment", defaultValue = "0")
    var isCardPayment: Boolean = false,

    @ColumnInfo(name = "last_recurrence", defaultValue = "0")
    var lastRecurrence: Long = 0,

    @ColumnInfo(name = "blob_key") // Assuming this is a String, adjust if it's a byte array
    var blobKey: String? = null,

    @ColumnInfo(name = "updated_on", defaultValue = "0") // From DatabaseAdapter insertTransaction
    var updatedOn: Long = 0
)
