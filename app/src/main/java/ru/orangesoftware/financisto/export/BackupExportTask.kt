package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import ru.orangesoftware.financisto.backup.DatabaseExport
import ru.orangesoftware.financisto.db.DatabaseAdapter

class BackupExportTask(
	context: Activity,
	dialog: ProgressDialog,
	private val uploadOnline: Boolean
) : ImportExportAsyncTask(context, dialog) {

	@Volatile
    var backupFileName: String? = null

	override fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? {
		val export = DatabaseExport(context, db.db(), true)
		backupFileName = export.export()
		if (uploadOnline) {
			doUploadToDropbox(context, backupFileName)
//			doUploadToGoogleDrive(context, backupFileName)
		}
		return backupFileName
	}

	override fun getSuccessMessage(result: Any?): String = result.toString()
}
