package ru.orangesoftware.financisto.view

import android.content.Context

import ru.orangesoftware.financisto.model.Attribute

object AttributeViewFactory {
    @JvmStatic
    fun createViewForAttribute(
        context: Context,
        attribute: Attribute,
    ): AttributeView? = when (attribute.type) {
        Attribute.TYPE_TEXT -> TextAttributeView(context, attribute)
        Attribute.TYPE_NUMBER -> NumberAttributeView(context, attribute)
        Attribute.TYPE_LIST -> ListAttributeView(context, attribute)
        Attribute.TYPE_CHECKBOX -> CheckBoxAttributeView(context, attribute)
        else -> null
    }
}
