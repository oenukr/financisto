package ru.orangesoftware.financisto.db

import android.content.ContentValues
import ru.orangesoftware.financisto.db.DatabaseHelper.LOCATIONS_TABLE
import ru.orangesoftware.orb.Plugin

class DatabaseFixPlugin : Plugin {
    override fun withContentValues(tableName: String?, values: ContentValues?) {
        if (LOCATIONS_TABLE == tableName) {
            // since there is no easy way to drop a column in SQLite
            values?.put("name", values.getAsString("title"))
        }
    }
}
