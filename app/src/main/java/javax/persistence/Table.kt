package javax.persistence

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Table(
    val name: String = "",
)
