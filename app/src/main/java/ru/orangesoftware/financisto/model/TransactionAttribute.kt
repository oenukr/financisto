package ru.orangesoftware.financisto.model

import android.content.ContentValues
import android.database.Cursor

import ru.orangesoftware.financisto.db.DatabaseHelper.TransactionAttributeColumns

class TransactionAttribute(
    var attributeId: Long,
    var transactionId: Long? = null,
    var value: String? = null,
) {
    companion object {
        @JvmStatic
        fun fromCursor(c: Cursor): TransactionAttribute = TransactionAttribute(
            attributeId = c.getLong(TransactionAttributeColumns.Indicies.ATTRIBUTE_ID),
            transactionId = c.getLong(TransactionAttributeColumns.Indicies.TRANSACTION_ID),
            value = c.getString(TransactionAttributeColumns.Indicies.VALUE),
        )
    }

    fun toValues(): ContentValues = ContentValues().apply {
        put(TransactionAttributeColumns.TRANSACTION_ID, transactionId)
        put(TransactionAttributeColumns.ATTRIBUTE_ID, attributeId)
        put(TransactionAttributeColumns.VALUE, value)
    }
}
