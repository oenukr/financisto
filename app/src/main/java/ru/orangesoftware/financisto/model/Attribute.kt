package ru.orangesoftware.financisto.model

import android.content.ContentValues
import android.database.Cursor
import androidx.room.ColumnInfo
import androidx.room.Entity
import ru.orangesoftware.financisto.db.DatabaseHelper.ATTRIBUTES_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.AttributeColumns
import ru.orangesoftware.orb.EntityManager.DEF_SORT_COL

@Entity(tableName = ATTRIBUTES_TABLE)
data class Attribute(
    @ColumnInfo(name = "type") var type: Int,
    @ColumnInfo(name = "list_values") var listValues: String?,
    @ColumnInfo(name = "default_value") var defaultValue: String?,
    @ColumnInfo(name = DEF_SORT_COL) override var sortOrder: Long,
) : MyEntity(), SortableEntity {

    companion object {
        const val DELETE_AFTER_EXPIRED_ID: Long = -1

        const val TYPE_TEXT: Int = 1
        const val TYPE_NUMBER: Int = 2
        const val TYPE_LIST: Int = 3
        const val TYPE_CHECKBOX: Int = 4

        fun deleteAfterExpired(): Attribute {
            return Attribute(
                type = TYPE_CHECKBOX,
                listValues = null,
                defaultValue = "true",
                sortOrder = 0
            ).apply {
                id = DELETE_AFTER_EXPIRED_ID
                title = "DELETE_AFTER_EXPIRED"
            }
        }

        @JvmStatic
        fun fromCursor(cursor: Cursor): Attribute = Attribute(
                type = cursor.getInt(AttributeColumns.Indicies.TYPE),
                listValues = cursor.getString(AttributeColumns.Indicies.LIST_VALUES),
                defaultValue = cursor.getString(AttributeColumns.Indicies.DEFAULT_VALUE),
                sortOrder = cursor.getLong(5)
        ).apply {
            id = cursor.getLong(AttributeColumns.Indicies.ID)
            title = cursor.getString(AttributeColumns.Indicies.TITLE)
        }
    }

    fun getTheDefaultValue(): String? {
        if (type == TYPE_CHECKBOX) {
            val values: List<String>? = listValues?.split(";")
            val checked: Boolean = defaultValue.toBoolean()
            values?.size?.let {
                if (it > 1) {
                    return values[if (checked) 0 else 1]
                }
            }
            return checked.toString()
        } else {
            return defaultValue
        }
    }

        fun toContentValues(): ContentValues = ContentValues().apply {
            put(AttributeColumns.TITLE, title)
            put(AttributeColumns.TYPE, type)
            put(AttributeColumns.LIST_VALUES, listValues)
            put(AttributeColumns.DEFAULT_VALUE, defaultValue)
        }
}
