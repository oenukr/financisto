package ru.orangesoftware.financisto.model

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import ru.orangesoftware.orb.EntityManager.DEF_ID_COL
import ru.orangesoftware.orb.EntityManager.DEF_TITLE_COL

open class MyEntity @JvmOverloads constructor(
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = DEF_ID_COL) override var id: Long = -1,
	@ColumnInfo(name = DEF_TITLE_COL) @Ignore override var title: String? = null,
	@ColumnInfo(name = "is_active") var isActive: Boolean = true,
	@Ignore @Transient override var checked: Boolean = false,
) : MultiChoiceItem {

	companion object {
		@JvmStatic
		fun splitIds(string: String): Array<Long>? {
			if (string.isEmpty()) return null

			return string.split(",")
				.map { it.toLong() }
				.toTypedArray()
		}

		@JvmStatic
		fun <T : MyEntity> asMap(list: List<T>): Map<Long, T> = list.associateBy { it.id }

		@JvmStatic
		fun indexOf(entities: List<MyEntity>?, id: Long): Int = entities?.indexOfFirst { it.id == id } ?: -1

		@JvmStatic
		fun <T : MyEntity> find(entities: List<T>, id: Long): T? = entities.find { it.id == id }
	}

	override fun toString(): String = title.orEmpty()

	override fun equals(other: Any?): Boolean = other is MyEntity && id == other.id

	override fun hashCode(): Int {
		return hashCodeOf(id)
	}

	inline fun hashCodeOf(vararg values: Any?) = values.fold(0) { acc, value ->
		(acc * 31) + value.hashCode()
	}
}
