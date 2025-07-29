package ru.orangesoftware.orb

import android.database.Cursor

import androidx.sqlite.db.SupportSQLiteDatabase

import ru.orangesoftware.financisto.repository.utils.Logger

class Query<T>(
    em: EntityManager,
    private val clazz: Class<T>,
) {
    private val logger: Logger by inject()

    private val ed: ru.orangesoftware.orb.EntityDefinition = EntityManager.getEntityDefinitionOrThrow(clazz)
    private val db: SupportSQLiteDatabase = em.db()

    private val orderBy: MutableList<String> = mutableListOf()
    private var where: String? = null
    private lateinit var whereArgs: List<String>

    fun where(expression: Expression): Query<T> {
        val selection: Selection = expression.toSelection(ed)
        where = selection.selection
        whereArgs = selection.selectionArgs.toList()
        return this
    }

    fun sort(vararg sort: Sort): Query<T> {
        sort.forEach {
            if (it.asc) {
                asc(it.field)
            } else {
                desc(it.field)
            }
        }
        return this
    }

    fun asc(field: String): Query<T> {
        orderBy.add(ed.getColumnForField(field) + " asc")
        return this
    }

    fun desc(field: String): Query<T> {
        orderBy.add(ed.getColumnForField(field) + " desc")
        return this
    }

    fun execute(): Cursor {
        var query: String = ed.sqlQuery
        val where: String? = this.where
        val whereArgs: List<String>? = this.whereArgs
        val sb = StringBuilder(query)
        where?.also { sb.append(" where ").append(where) }
        if (!orderBy.isEmpty()) {
            sb.append(" order by ")
            var addComma = false
            orderBy.forEach {
                if (addComma) sb.append(", ")

                sb.append(it)
                addComma = true
            }
        }
        query = sb.toString()
        logger.d("QUERY %s: %s", clazz, query)
        logger.d("WHERE: %s", where.orEmpty())
        logger.d("ARGS: %s", whereArgs.orEmpty())
        return db.rawQuery(query, whereArgs.toTypedArray())
    }

    fun uniqueResult(): T? = execute().use {
        if (it.moveToFirst()) {
            EntityManager.loadFromCursor(it, clazz)
        } else {
            null
        }
    }

    fun list(): List<T> = readEntityList(execute(), clazz)

    companion object {
        @JvmStatic
        fun <T> readEntityList(cursorRes: Cursor, clazz: Class<T>): List<T> = cursorRes.use {
            mutableListOf<T>().apply {
                while (it.moveToNext()) {
                    EntityManager.loadFromCursor(it, clazz)?.also(::add)
                }
            }
        }
    }
}
