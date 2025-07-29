package javax.persistence

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class JoinColumn(
    val name: String,
    val required: Boolean = true
)
