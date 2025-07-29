package ru.orangesoftware.orb

import kotlin.reflect.KCallable
import kotlin.reflect.KMutableProperty

data class FieldInfo(
    val index: Int,
    var field: KMutableProperty<*>,
    val columnName: String,
    val type: FieldType,
    val required: Boolean,
) {

    companion object {
        fun primitive(
            field: KMutableProperty<*>,
            columnName: String,
        ): FieldInfo = FieldInfo(
            index = 0,
            field = field,
            columnName = columnName,
            type = FieldType.getType(field),
            required = false,
        )

        fun entity(
            index: Int,
            field: KMutableProperty<*>,
            columnName: String,
            required: Boolean,
        ): FieldInfo = FieldInfo(
            index = index,
            field = field,
            columnName = columnName,
            type = FieldType.ENTITY((field as KCallable<*>).returnType),
            required = required,
        )
    }

    override fun toString(): String = "[$index:$columnName,$type]"
}
