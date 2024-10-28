package ru.orangesoftware.financisto.adapter.async

import android.database.Cursor
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.SmsTemplate

class SmsTemplateListSource(
    private val db: DatabaseAdapter,
    prepareCursor: Boolean,
) : CursorItemSource<SmsTemplate>() {
    
    @Volatile
    private var filter: String? = null

    init {
        if (prepareCursor) prepareCursor()
    }

    override fun initCursor(): Cursor =
        db.getSmsTemplatesWithFullInfo(filter)

    override fun loadItem(): SmsTemplate =
        SmsTemplate.fromListCursor(cursor)

    override fun clazz(): Class<SmsTemplate> = SmsTemplate::class.java

    override fun setConstraint(constraint: CharSequence?) {
        filter = constraint.toString()
    }
}
