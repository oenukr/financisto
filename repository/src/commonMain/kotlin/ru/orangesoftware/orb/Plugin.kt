package ru.orangesoftware.orb

import android.content.ContentValues

interface Plugin {
    fun withContentValues(tableName: String, values: ContentValues)
}
