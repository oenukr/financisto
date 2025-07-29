package ru.orangesoftware.financisto.repository.model

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.LocalizableEnum

enum class SystemAttribute(
	val id: Long,
	override val titleId: Int,
) : LocalizableEnum {
	
	DELETE_AFTER_EXPIRED(-1, R.string.system_attribute_delete_after_expired);

	companion object {
		@JvmStatic
		private val idToAttribute = mutableMapOf<Long, SystemAttribute>()

		@JvmStatic
		fun forId(attributeId: Long): SystemAttribute? {
			return idToAttribute[attributeId]
		}

		init {
			entries.forEach {
				idToAttribute[it.id] = it
			}
		}
	}
}
