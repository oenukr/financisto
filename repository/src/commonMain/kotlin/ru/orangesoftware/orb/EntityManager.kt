package ru.orangesoftware.orb

import ru.orangesoftware.financisto.repository.local.DatabaseHelper.SmsTemplateColumns.sort_order

import android.content.ContentValues
import android.database.Cursor

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EntityNotFoundException
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.PersistenceException
import javax.persistence.Table
import javax.persistence.Transient

import ru.orangesoftware.financisto.repository.model.MyEntity
import ru.orangesoftware.financisto.repository.model.SortableEntity
import java.util.Locale.getDefault
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import androidx.core.database.sqlite.transaction
import ru.orangesoftware.financisto.repository.local.DatabaseUtils

abstract class EntityManager(
    protected val databaseHelper: SupportSQLiteOpenHelper,
    vararg plugins: Plugin,
) {

    companion object {
        private val definitions: ConcurrentMap<Class<*>, ru.orangesoftware.orb.EntityDefinition> = ConcurrentHashMap()

        const val DEF_ID_COL = "_id"
        const val DEF_TITLE_COL = "title"
        const val DEF_SORT_COL = "sort_order"

        private fun parseField(field: Field): FieldInfo {
            val columnName = if (field.isAnnotationPresent(Column::class.java)) {
                field.getAnnotation(Column::class.java)?.name
            } else {
                field.getName().uppercase(getDefault())
            }
            return FieldInfo.primitive(field, columnName.orEmpty())
        }

        @JvmStatic
        fun getEntityDefinitionOrThrow(clazz: Class<*>): ru.orangesoftware.orb.EntityDefinition {
            var ed: ru.orangesoftware.orb.EntityDefinition? = definitions[clazz]
            if (ed == null) {
                val ned: ru.orangesoftware.orb.EntityDefinition = parseDefinition(clazz)
                ed = definitions.putIfAbsent(clazz, ned)
                if (ed == null) {
                    ed = ned
                }
            }
            return ed
        }

        private fun parseDefinition(clazz: Class<*>): ru.orangesoftware.orb.EntityDefinition {
            if (!clazz.isAnnotationPresent(Entity::class.java)) {
                throw IllegalArgumentException("Class $clazz is not an @Entity")
            }
            val edb: ru.orangesoftware.orb.EntityDefinition.Builder = ru.orangesoftware.orb.EntityDefinition.Builder(clazz)
            try {
                val constructor: Constructor<*> = clazz.getConstructor()
                edb.withConstructor(constructor);
            } catch (e: Exception) {
                throw IllegalArgumentException("Entity must have an empty constructor")
            }
            if (clazz.isAnnotationPresent(Table::class.java)) {
                val tableAnnotation: Table? = clazz.getAnnotation(Table::class.java)
                edb.withTable(tableAnnotation?.name)
            }
            val fields: Array<Field> = clazz.getFields()
            if (fields != null) {
                var index = 0
                fields.forEach {
                    if ((it.modifiers and Modifier.STATIC) == 0) {
                        if (it.isAnnotationPresent(Id::class.java)) {
                            edb.withIdField(parseField(it))
                        } else {
                            if (!it.isAnnotationPresent(Transient::class.java)) {
                                if (it.isAnnotationPresent(JoinColumn::class.java)) {
                                    val column: JoinColumn? = it.getAnnotation(JoinColumn::class.java)
                                    edb.withField(
                                        FieldInfo.entity(
                                            index = index++,
                                            field = it,
                                            columnName = column?.name.orEmpty(),
                                            required = column?.required == true,
                                        )
                                    )
                                } else {
                                    edb.withIdField(parseField(it))
                                }
                            }
                        }
                    }
                }
            }
            return edb.create()
        }

        @JvmStatic
        fun <T> loadFromCursor(cursor: Cursor, clazz: Class<T>): T? {
            val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(clazz)
            try {
                return loadFromCursor("e", cursor, ed)
            } catch (e: Exception) {
                throw PersistenceException("Unable to load entity of type $clazz from cursor", e);
            }
        }

        @Throws(Exception)
        private fun <T> loadFromCursor(pe: String, cursor: Cursor, ed: ru.orangesoftware.orb.EntityDefinition): T? {
            val idIndex: Int = cursor.getColumnIndexOrThrow("${pe}__id")
            if (cursor.isNull(idIndex)) {
                return null
            }
            @SuppressWarnings("unchecked")
            val entity: T = ed.constructor.newInstance() as T
            val fields: Array<FieldInfo> = ed.fields
            fields.forEach { fi ->
                val value: Any? = if (fi.type.isPrimitive()) {
                    fi.type.getValueFromCursor(cursor, "${pe}_${fi.columnName}")
                } else {
                    val eed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(fi.field.type)
                    loadFromCursor("${pe}${fi.index}", cursor, eed)
                }
                fi.field.set(entity, value)
            }
            return entity
        }
    }

    private val plugins: List<Plugin> = plugins.toList()

    fun db(): SupportSQLiteDatabase = databaseHelper.writableDatabase

    fun <T : MyEntity> duplicate(clazz: Class<T>, id: Any): Long {
        val obj: T? = load(clazz, id)
        if (obj == null) return -1

        obj.id = -1
        updateEntitySortOrder(obj, -1)
        return saveOrUpdate(obj)
    }

    fun <T : MyEntity> updateEntitySortOrder(obj: T, sortOrder: Long): Boolean {
        if (obj is SortableEntity) {
            val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(obj.javaClass)
            try {
                ed.fields.forEach {
                    if (DEF_SORT_COL == it.columnName) {
                        it.field.set(obj, sortOrder)
                        return true
                    }
                }
            } catch (e: IllegalAccessException) {
                throw IllegalStateException(
                    String.format("Failed to reset sort order for %s", obj.javaClass),
                    e,
                )
            }
        }
        return false
    }

    fun saveOrUpdate(entity: Any): Long {
        val db: SupportSQLiteDatabase = db()
        val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(entity.javaClass)
        val values: ContentValues = getContentValues(ed, entity)
        var id: Long = ed.getId(entity)
        values.remove("updated_on")
        values.put("updated_on", System.currentTimeMillis())
        if (id <= 0) {
            values.remove(ed.idField.columnName)
            if (values.containsKey(DEF_SORT_COL) && values.getAsLong(DEF_SORT_COL) <= 0) {
                values.put(DEF_SORT_COL, getMaxOrder(ed) + 1)
            }
            id = db.insert(ed.tableName, CONFLICT_NONE, values)
            ed.setId(entity, id)
        } else {
            values.remove("updated_on")
            values.put("updated_on", System.currentTimeMillis())
            db.update(
                ed.tableName,
                values,
                ed.idField.columnName + "=?",
                arrayOf(id.toString())
            )
        }
        return id
    }

    private fun getMaxOrder(ed: ru.orangesoftware.orb.EntityDefinition): Long = DatabaseUtils.rawFetchLong(
        db(),
        String.format("select max(%s) from %s", DEF_SORT_COL, ed.tableName),
        emptyArray<String>(),
        0,
    )

    fun reInsert(entity: Any): Long {
        val db: SupportSQLiteDatabase = db()
        val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(entity.javaClass)
        val values: ContentValues = getContentValues(ed, entity)
        val id: Long = ed.getId(entity)
        val newId: Long = db.insert(
            ed.tableName,
            CONFLICT_NONE,
            values,
        )
        if (id != newId) {
            throw IllegalArgumentException("Unable to re-insert ${entity.javaClass} with id $id")
        }
        return id
    }

    private fun getContentValues(ed: ru.orangesoftware.orb.EntityDefinition, entity: Any): ContentValues {
        val values: ContentValues = ContentValues()
        val fields: Array<FieldInfo> = ed.fields
        fields.forEach { fi ->
            try {
                if (fi.type.isPrimitive()) {
                    val value: Any? = fi.field.get(entity)
                    fi.type.setValue(values, fi.columnName, value)
                } else {
                    val e: Any? = fi.field.get(entity)
                    if (e == null) {
                        values.putNull(fi.columnName)
                    } else {
                        val eed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(e.javaClass)
                        val ffi: FieldInfo = eed.idField
                        val value: Any? = ffi.field.get(e)
                        ffi.type.setValue(values, fi.columnName, value)
                    }
                }
            } catch (e: Exception) {
                throw PersistenceException("Unable to create content values for $entity", e)
            }
        }
        return applyPlugins(ed.tableName, values)
    }

    private fun applyPlugins(tableName: String, values: ContentValues): ContentValues {
        plugins.forEach {
            it.withContentValues(tableName, values)
        }
        return values
    }

    fun <T> load(clazz: Class<T>, id: Any): T {
        val e: T? = get(clazz, id)
        if (e != null) {
            return e
        } else {
            throw EntityNotFoundException(clazz, id)
        }
    }

    fun <T> get(clazz: Class<T>, id: Any): T? {
        val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(clazz)
        val sql: String = "${ed.sqlQuery} where e_${ed.idField.columnName}=?"
        db().query(sql, arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                try {
                    return loadFromCursor("e", cursor, ed)
                } catch (e: Exception) {
                    throw PersistenceException(
                        "Unable to load entity of type $clazz with id $id",
                        e,
                    )
                }
            }
        }
        return null
    }

    fun <T> list(clazz: Class<T>): List<T?> {
        val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(clazz)
        return db().query(ed.sqlQuery, null).use { cursor ->
            val list = mutableListOf<T?>()
            while (cursor.moveToNext()) {
                try {
                    val t: T? = loadFromCursor("e", cursor, ed)
                    list.add(t)
                } catch (e: Exception) {
                    throw PersistenceException("Unable to list entites of type $clazz", e)
                }
            }
            list
        }
    }

    fun <T> delete(clazz: Class<T>, id: Any): Int {
        val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(clazz)
        return db().delete(
            ed.tableName,
            ed.idField.columnName + "=?",
            arrayOf(id.toString()),
        )
    }

    fun <T> createQuery(clazz: Class<T>): Query<T> = Query(this, clazz)

    fun <T : SortableEntity> getNextByOrder(entityClass: Class<T>, itemId: Long): Long {
        val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(entityClass)
        val item: T? = get(entityClass, itemId)
        var res: Long = -1
        if (item != null) {
            res = DatabaseUtils.rawFetchLong(
                db(),
                String.format(
                    "select %1$s from %2$s where %3$s > ? order by %3$s asc limit 1",
                    DEF_ID_COL,
                    ed.tableName,
                    DEF_SORT_COL
                ),
                arrayOf(item.getSortOrder().toString()),
                res
            )
        }
        return res
    }

    fun <T : SortableEntity> moveItemByChangingOrder(
        entityClass: Class<T>,
        movedId: Long,
        targetId: Long,
    ): Boolean {
        if (movedId > 0 && targetId > 0 && movedId != targetId) {
            val ed: ru.orangesoftware.orb.EntityDefinition = getEntityDefinitionOrThrow(entityClass)

            val sourceItem: T = load(entityClass, movedId)
            val srcOrder: Long = sourceItem.getSortOrder()
            val targetOrder: Long = load(entityClass, targetId).getSortOrder()
            val db: SupportSQLiteDatabase = db()
            db.transaction {
                if (srcOrder > targetOrder) {
                    execSQL(
                        String.format(
                            "update %1$s set %2$s = %2$s + 1 where %2$s >= ? and %2$s < ? ",
                            ed.tableName,
                            sort_order
                        ),
                        arrayOf(targetOrder.toString(), srcOrder.toString()),
                    )
                } else if (srcOrder < targetOrder) {
                    execSQL(
                        String.format(
                            "update %1$s set %2$s = %2$s - 1 where %2$s > ? and %2$s <= ? ",
                            ed.tableName,
                            sort_order
                        ),
                        arrayOf(srcOrder.toString(), targetOrder.toString()),
                        )
                }
                val cv: ContentValues = ContentValues (1)
                cv.put(DEF_SORT_COL, targetOrder)
                update(
                    ed.tableName,
                    CONFLICT_NONE,
                    cv,
                    "$DEF_ID_COL=?",
                    arrayOf(movedId.toString())
                )

                return true
            }
        }
        return false
    }
}
