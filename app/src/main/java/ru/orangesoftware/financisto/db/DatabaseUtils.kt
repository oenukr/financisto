package ru.orangesoftware.financisto.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import ru.orangesoftware.financisto.model.MyEntity

object DatabaseUtils {
    @JvmStatic
    fun rawFetchId(db: DatabaseAdapter, query: String, selectionArgs: Array<String>): Long {
        return rawFetchLong(db.db(), query, selectionArgs, -1)
    }

    @JvmStatic
    fun rawFetchLongValue(db: DatabaseAdapter, query: String, selectionArgs: Array<String>): Long {
        return rawFetchLong(db.db(), query, selectionArgs, 0)
    }

    @JvmStatic
    fun rawFetchLong(db: SQLiteDatabase, query: String, selectionArgs: Array<String>, defaultValue: Long): Long {
        db.rawQuery(query, selectionArgs).use { c ->
            if (c.moveToFirst())
                return c.getLong(0)
            }
        return defaultValue
    }

    @JvmStatic
    fun generateSelectClause(fields: Array<String>, prefix: String?): String {
        val res = StringBuilder()
        fields.forEach { f ->
            if (res.isNotEmpty()) {
                res.append(", ")
            }
            if (!prefix.isNullOrBlank()) {
                res.append(prefix).append(".")
            }
            res.append(f)
        }
        return res.toString()
    }

    @JvmStatic
    fun <T : MyEntity>  cursorToList(c: Cursor, f: EntitySupplier<T>): List< T> {
        // todo.mb: consider implementing limit here, e.g. 1000 items max to prevent memory issues
        val res = mutableListOf<T>()
        while (c.moveToNext()) {
            val a: T = f.fromCursor(c)
            res.add(a)
        }
        return res
    }

    interface EntitySupplier<T> {
        fun fromCursor(c: Cursor): T
    }
}
