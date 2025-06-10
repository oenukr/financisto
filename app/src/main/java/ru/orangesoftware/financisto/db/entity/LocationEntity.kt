package ru.orangesoftware.financisto.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper

@Entity(tableName = DatabaseHelper.LOCATIONS_TABLE)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.TITLE)
    var title: String,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.DATETIME, defaultValue = "0")
    var dateTime: Long = 0,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.PROVIDER)
    var provider: String? = null,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.ACCURACY, defaultValue = "0.0")
    var accuracy: Double = 0.0,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.LATITUDE, defaultValue = "0.0")
    var latitude: Double = 0.0,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.LONGITUDE, defaultValue = "0.0")
    var longitude: Double = 0.0,

    @ColumnInfo(name = DatabaseHelper.LocationColumns.IS_PAYEE, defaultValue = "0")
    var isPayee: Boolean = false, // Assuming boolean, adjust if Int

    @ColumnInfo(name = DatabaseHelper.LocationColumns.RESOLVED_ADDRESS)
    var resolvedAddress: String? = null,

    @ColumnInfo(name = "is_active", defaultValue = "1") // Common pattern from MyEntityManager
    var isActive: Boolean = true,

    @ColumnInfo(name = "count", defaultValue = "0") // From DatabaseAdapter.LOCATION_COUNT_UPDATE
    var count: Int = 0
)
