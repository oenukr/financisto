package ru.orangesoftware.orb

import javax.persistence.PersistenceException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

class EntityDefinition(
	val constructor: KFunction<*>,
	val tableName: String,
	val idField: FieldInfo,
	val fields: Array<FieldInfo>
) {

	val sqlQuery: String
	private val fieldToInfoMap = mutableMapOf<String, FieldInfo>()

	init {
		sqlQuery = prepareSqlQuery()
		prepareColumns()
	}

	companion object {
		class Builder(private val clazz: KClass<*>) {
			private var constructor: KFunction<*>? = null
			private var tableName: String? = null
			private var idField: FieldInfo? = null
			private val fields = mutableListOf<FieldInfo>()

			init {
				constructor = clazz.primaryConstructor
			}

			fun withConstructor(constructor: KFunction<*>) = apply {
				this.constructor = constructor
			}

			fun withTable(tableName: String) = apply {
				this.tableName = tableName
			}

			fun withIdField(fieldInfo: FieldInfo) = apply {
				idField = fieldInfo
				fields.add(fieldInfo)
			}

			fun withField(fieldInfo: FieldInfo) = apply {
				fields.add(fieldInfo)
			}

			fun create(): EntityDefinition {
				val actualTableName = tableName ?: clazz.simpleName?.uppercase()
				?: throw IllegalArgumentException("Table name can not be null")
				val actualConstructor = constructor ?: clazz.primaryConstructor
				?: throw IllegalArgumentException("Constructor can not be null")
				val actualIdField = idField ?: throw IllegalArgumentException("Id field can not be null")
				return EntityDefinition(
					actualConstructor,
					actualTableName,
					actualIdField,
					fields.toTypedArray()
				)
			}
		}
	}

	fun getId(entity: Any): Long {
		return try {
			idField.field.getLong(entity)
		} catch (e: Exception) {
			throw PersistenceException("Unable to get id from $entity", e)
		}
	}

	fun setId(entity: Any, id: Long) {
		try {
			idField.field.setLong(entity, id)
		} catch (e: Exception) {
			throw PersistenceException("Unable to set id for $entity", e)
		}
	}

	fun getColumnForField(field: String): String {
		if (field.contains('.')) {
			val path = field.split(".")
			val e = StringBuilder("e")
			var currentEd: EntityDefinition = this
			for (f in path) {
				val fi = currentEd.getFieldInfo(f)
				if (fi.type.isPrimitive()) {
					e.append("_").append(fi.columnName)
					break
				} else {
					e.append(fi.index)
					currentEd = EntityManager.getEntityDefinitionOrThrow(fi.field.type)
				}
			}
			return e.toString()
		} else {
			val fi = getFieldInfo(field)
			return "e_${fi.columnName}"
		}
	}

	private fun getFieldInfo(field: String): FieldInfo {
		return fieldToInfoMap[field] ?: throw IllegalArgumentException(
			"Unknown field [$field] for ${constructor.returnType.classifier}"
		)
	}

	private fun prepareSqlQuery(): String {
		val sb1 = StringBuilder("select ")
		sb1.append("e.").append(idField.columnName).append(" as ").append(EntityManager.DEF_ID_COL)
		val sb2 = StringBuilder()
		sb2.append(" from ").append(tableName).append(" as e")
		prepareSqlQuery(this, sb1, sb2, "e", true)
		return sb1.append(sb2).toString()
	}

	private fun prepareSqlQuery(
		ed: EntityDefinition,
		sbColumns: StringBuilder,
		sbJoins: StringBuilder,
		pe: String,
		required: Boolean
	) {
		for (f in ed.fields) {
			if (f.type.isPrimitive()) {
				appendColumn(sbColumns, pe, f.columnName)
			} else {
				val e = pe + f.index
				val isRequired = required && f.required
				val edJoin = EntityManager.getEntityDefinitionOrThrow(f.field.type)
				sbJoins.append(if (isRequired) " inner join " else " left outer join ")
					.append(edJoin.tableName).append(" as ").append(e)
				sbJoins.append(" on ").append(e).append(".").append(ed.idField.columnName)
					.append("=").append(pe).append(".").append(f.columnName)
				prepareSqlQuery(edJoin, sbColumns, sbJoins, e, isRequired)
			}
		}
	}

	private fun prepareColumns() {
		fields.forEach { fieldToInfoMap[it.field.name] = it }
	}

	private fun appendColumn(sb: StringBuilder, e: String, c: String) {
		sb.append(", ").append(e).append(".").append(c).append(" as ").append(e).append("_").append(c)
	}
}
