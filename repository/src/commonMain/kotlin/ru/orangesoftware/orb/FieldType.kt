package ru.orangesoftware.orb

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class FieldType {

    abstract fun valueFromCursor(cursor: Cursor, columnIndex: Int): Any?
    abstract fun putValue(values: ContentValues, key: String, value: Any)

    open fun isPrimitive(): Boolean = true

    object DOUBLE : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Double =
            cursor.getDouble(columnIndex)

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, value as Double)
        }
    }

    object FLOAT : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Float =
            cursor.getFloat(columnIndex)

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, value as Float)
        }
    }

    object INT : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Int =
            cursor.getInt(columnIndex)

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, value as Int)
        }
    }

    object LONG : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Long =
            cursor.getLong(columnIndex)

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, value as Long)
        }
    }

    object SHORT : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Short =
            cursor.getShort(columnIndex)

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, value as Short)
        }
    }

    object STRING : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): String =
            cursor.getString(columnIndex)

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, value as String)
        }
    }

    object BOOLEAN : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Boolean =
            cursor.getInt(columnIndex) == 1

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, if (value == true) 1 else 0)
        }
    }

    @OptIn(ExperimentalTime::class)
    object DATE : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Instant? {
            val d = cursor.getLong(columnIndex)
            return if (d == 0L) null else Instant.fromEpochMilliseconds(d)
        }

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, (value as Instant).toEpochMilliseconds())
        }
    }

    data class ENUM(val enumType: KClass<out Enum<*>>) : FieldType() {
        override fun valueFromCursor(cursor: Cursor, columnIndex: Int): Enum<*>? {
            val name = cursor.getString(columnIndex)
            return name?.let { Enum.valueOf(enumType, it) }
        }

        override fun putValue(values: ContentValues, key: String, value: Any) {
            values.put(key, (value as Enum<*>).name)
        }
    }

    data class ENTITY(val clazz: KType) : FieldType() {
        override fun putValue(values: ContentValues, key: String, value: Any) {
            throw UnsupportedOperationException()
        }

        override fun valueFromCursor(c: Cursor, columnIndex: Int): Any {
            throw UnsupportedOperationException()
        }

        override fun isPrimitive(): Boolean = false
    }

    @Throws(IllegalArgumentException::class)
    fun getValueFromCursor(cursor: Cursor, columnName: String): Any? {
        val columnIndex = cursor.getColumnIndexOrThrow(columnName)
        return valueFromCursor(cursor, columnIndex)
    }

    fun setValue(values: ContentValues, key: String, value: Any?) {
        value?.let { putValue(values, key, it) } ?: values.putNull(key)
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        fun getType(field: KProperty<*>): FieldType {
            val clazz = (field as KCallable<*>).returnType

            if (clazz.isEnum) return ENUM(clazz as KClass<out Enum<*>>)

            return when(clazz) {
                Double::class, Double -> DOUBLE
                Float::class, Float -> FLOAT
                Int::class, Int -> INT
                Long::class, Long -> LONG
                Short::class, Short -> SHORT
                Boolean::class, Boolean -> BOOLEAN
                String::class, String -> STRING
                Instant::class -> DATE
                else -> throw IllegalArgumentException("Field [$field] has unsupported type.")
            }
        }
    }
}
