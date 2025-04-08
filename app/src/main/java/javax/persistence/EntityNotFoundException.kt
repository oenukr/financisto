package javax.persistence

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

@Serializable
class EntityNotFoundException(
	val entityClass: KClass<*>,
	@Contextual
	val id: Any?,
	@Transient
	override val cause: Throwable? = null
) : PersistenceException(getDetailMessage(entityClass, id), cause) {
	companion object {
		private fun getDetailMessage(entityClass: KClass<*>, id: Any?): String {
			return "Unable to find an entity of type $entityClass with id $id"
		}
	}
}
