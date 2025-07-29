package ru.orangesoftware.orb

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import timber.log.Timber
import java.io.IOException

object BinaryAnnotationReader {

    fun <T : Annotation> getBinaryAnnotation(
        clazz: Class<*>,
        annotationClass: Class<T>
    ): T? {
        val annotationDescriptor = "L${annotationClass.name.replace('.', '/')};"
        var foundAnnotation: T? = null

        try {
            val resourceName = "${clazz.name.replace('.', '/')}.class"
            clazz.classLoader?.getResourceAsStream(resourceName)?.use { inputStream ->
                val classReader = ClassReader(inputStream)

                classReader.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(
                        descriptor: String?,
                        visible: Boolean
                    ): AnnotationVisitor? {
                        if (descriptor == annotationDescriptor && !visible) {
                            return object : AnnotationVisitor(Opcodes.ASM9) {
                                override fun visit(name: String?, value: Any?) {
                                    // This is a simplified way to create the annotation instance.
                                    // For complex annotations, you might need a more robust approach.
                                    if (name == "value") {
                                        val annotationParams = mapOf(name to value)
                                        foundAnnotation = createAnnotationInstance(
                                            annotationClass,
                                            annotationParams
                                        )
                                    }
                                }
                            }
                        }
                        return super.visitAnnotation(descriptor, visible)
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        } catch (e: IOException) {
            Timber.e(e)
        }

        return foundAnnotation
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Annotation> createAnnotationInstance(
        annotationClass: Class<T>,
        values: Map<String, Any?>
    ): T {
        return java.lang.reflect.Proxy.newProxyInstance(
            annotationClass.classLoader,
            arrayOf<Class<*>>(annotationClass)
        ) { _, method, _ ->
            values[method.name]
        } as T
    }
}
