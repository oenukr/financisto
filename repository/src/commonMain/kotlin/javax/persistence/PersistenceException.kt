package javax.persistence

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

// Base exception for all persistence-related problems
@Serializable
open class PersistenceException(
	override val message: String? = null,
	@Contextual
	override val cause: Throwable? = null,
) : RuntimeException(message, cause) {
	open val serialVersionUID: Long = 1L
}
