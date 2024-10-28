package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.backup.DatabaseImport
import ru.orangesoftware.financisto.db.DatabaseAdapter

class BackupImportTask(
    activity: Activity,
    dialog: ProgressDialog,
) : ImportExportAsyncTask(activity, dialog) {

    override fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any {
        DatabaseImport.createFromFileBackup(context, db, params[0]).importDatabase()
        return true
    }

    override fun getSuccessMessage(result: Any?): String =
        context.getString(R.string.restore_database_success)
}
