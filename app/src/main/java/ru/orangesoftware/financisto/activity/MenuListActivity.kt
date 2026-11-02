// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.view.View
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.GooglePlayServicesUtil
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.SummaryEntityListAdapter
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.export.csv.CsvExportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.drive.DoDriveBackup
import ru.orangesoftware.financisto.export.drive.DoDriveListFiles
import ru.orangesoftware.financisto.export.drive.DoDriveRestore
import ru.orangesoftware.financisto.export.drive.DriveBackupError
import ru.orangesoftware.financisto.export.drive.DriveBackupFailure
import ru.orangesoftware.financisto.export.drive.DriveBackupSuccess
import ru.orangesoftware.financisto.export.drive.DriveConnectionFailed
import ru.orangesoftware.financisto.export.drive.DriveFileInfo
import ru.orangesoftware.financisto.export.drive.DriveFileList
import ru.orangesoftware.financisto.export.drive.DriveRestoreSuccess
import ru.orangesoftware.financisto.export.drive.DropboxFileList
import ru.orangesoftware.financisto.export.dropbox.DropboxBackupTask
import ru.orangesoftware.financisto.export.dropbox.DropboxListFilesTask
import ru.orangesoftware.financisto.export.dropbox.DropboxRestoreTask
import ru.orangesoftware.financisto.export.qif.QifExportOptions
import ru.orangesoftware.financisto.export.qif.QifImportOptions
import ru.orangesoftware.financisto.service.DailyAutoBackupScheduler.Companion.scheduleNextAutoBackup
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.utils.SummaryEntityEnum

@Suppress("TooManyFunctions", "DEPRECATION", "UNUSED_PARAMETER")
open class MenuListActivity : FragmentActivity() {

    private val bus: GreenRobotBus = DependenciesHolder().greenRobotBus
    private var progressDialog: ProgressDialog? = null

    private var listViewInternal: ListView? = null
    private var listAdapterInternal: ListAdapter? = null

    val listView: ListView
        get() = ensureListView()

    fun getListAdapter(): ListAdapter? = listAdapterInternal

    fun setListAdapter(adapter: ListAdapter?) {
        listAdapterInternal = adapter
        if (listViewInternal != null || findViewById<View?>(android.R.id.list) != null) {
            ensureListView().adapter = adapter
        }
    }

    private fun ensureListView(): ListView {
        var lv = listViewInternal
        if (lv == null) {
            lv = findViewById(android.R.id.list)
                ?: error("Your content must have a ListView whose id attribute is 'android.R.id.list'")
            lv.setOnItemClickListener { parent, view, position, id ->
                onListItemClick(parent as ListView, view, position, id)
            }
            findViewById<View?>(android.R.id.empty)?.let { emptyView ->
                lv.emptyView = emptyView
            }
            if (listAdapterInternal != null) {
                lv.adapter = listAdapterInternal
            }
            listViewInternal = lv
        }
        return lv
    }

    override fun onContentChanged() {
        super.onContentChanged()
        ensureListView()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    open fun init() {
        val items = MenuListItem.entries.toTypedArray<SummaryEntityEnum>()
        setListAdapter(SummaryEntityListAdapter(this, items))
    }

    protected open fun onListItemClick(l: ListView, v: View?, position: Int, id: Long) {
        MenuListItem.entries[position].call(this)
    }

    open fun onCsvExportResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val options = CsvExportOptions.fromIntent(data)
            MenuListItem.doCsvExport(this, options)
        }
    }

    open fun onQifExportResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val options = QifExportOptions.fromIntent(data)
            MenuListItem.doQifExport(this, options)
        }
    }

    open fun onCsvImportResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val options = CsvImportOptions.fromIntent(data)
            MenuListItem.doCsvImport(this, options)
        }
    }

    open fun onQifImportResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val options = QifImportOptions.fromIntent(data)
            MenuListItem.doQifImport(this, options)
        }
    }

    open fun onChangePreferences() {
        scheduleNextAutoBackup(this)
    }

    override fun onPause() {
        super.onPause()
        PinProtection.lock(this)
        bus.unregister(this)
    }

    override fun onResume() {
        super.onResume()
        PinProtection.unlock(this)
        bus.register(this)
    }

    override fun onDestroy() {
        dismissProgressDialog()
        super.onDestroy()
    }

    private fun dismissProgressDialog() {
        progressDialog?.let { dialog ->
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
        progressDialog = null
    }

    // google drive

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doGoogleDriveBackup(e: StartDriveBackup) {
        progressDialog = ProgressDialog.show(this, null, getString(R.string.backup_database_gdocs_inprogress), true)
        bus.post(DoDriveBackup())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doGoogleDriveRestore(e: StartDriveRestore) {
        progressDialog = ProgressDialog.show(this, null, getString(R.string.google_drive_loading_files), true)
        bus.post(DoDriveListFiles())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveList(event: DriveFileList) {
        dismissProgressDialog()
        val files = event.files ?: return
        val fileNames: Array<CharSequence?> = getFileNames(files)
        var selectedDriveFile: DriveFileInfo? = null
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_database_online_google_drive)
            .setPositiveButton(R.string.restore) { _, _ ->
                selectedDriveFile?.let { file ->
                    progressDialog = ProgressDialog.show(
                        this,
                        null,
                        getString(R.string.google_drive_restore_in_progress),
                        true,
                    )
                    bus.post(DoDriveRestore(file))
                }
            }
            .setSingleChoiceItems(fileNames, -1) { _, which ->
                if (which in files.indices) {
                    selectedDriveFile = files[which]
                }
            }
            .show()
    }

    private fun getFileNames(files: List<DriveFileInfo>): Array<CharSequence?> =
        Array(files.size) { i -> files[i].title }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveConnectionFailed(event: DriveConnectionFailed) {
        dismissProgressDialog()
        val connectionResult = event.connectionResult
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE)
            } catch (e: IntentSender.SendIntentException) {
                onDriveBackupError(DriveBackupError(e.message))
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.errorCode, this, 0)?.show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveBackupFailed(event: DriveBackupFailure) {
        dismissProgressDialog()
        val status = event.status
        if (status.hasResolution()) {
            try {
                status.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE)
            } catch (e: IntentSender.SendIntentException) {
                onDriveBackupError(DriveBackupError(e.message))
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(status.statusCode, this, 0)?.show()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveBackupSuccess(event: DriveBackupSuccess) {
        dismissProgressDialog()
        Toast.makeText(
            this,
            getString(R.string.google_drive_backup_success, event.fileName),
            Toast.LENGTH_LONG,
        ).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveRestoreSuccess(event: DriveRestoreSuccess) {
        dismissProgressDialog()
        Toast.makeText(this, R.string.restore_database_success, Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveBackupError(event: DriveBackupError) {
        dismissProgressDialog()
        Toast.makeText(
            this,
            getString(R.string.google_drive_connection_failed, event.message),
            Toast.LENGTH_LONG,
        ).show()
    }

    open fun onConnectionRequest(resultCode: Int) {
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.google_drive_connection_resolved, Toast.LENGTH_LONG).show()
        }
    }

    // dropbox

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doImportFromDropbox(event: DropboxFileList) {
        val backupFiles = event.files ?: return
        val items: Array<CharSequence?> = Array(backupFiles.size) { i -> backupFiles[i] }
        var selectedDropboxFile: String? = null
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_database_online_dropbox)
            .setPositiveButton(R.string.restore) { _, _ ->
                val file = selectedDropboxFile
                if (file != null) {
                    val d = ProgressDialog.show(
                        this,
                        null,
                        getString(R.string.restore_database_inprogress_dropbox),
                        true,
                    )
                    DropboxRestoreTask(this, d, file).execute()
                }
            }
            .setSingleChoiceItems(items, -1) { _, which ->
                if (which in backupFiles.indices) {
                    selectedDropboxFile = backupFiles[which]
                }
            }
            .show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doDropboxBackup(e: StartDropboxBackup) {
        val d = ProgressDialog.show(this, null, getString(R.string.backup_database_dropbox_inprogress), true)
        progressDialog = d
        DropboxBackupTask(this, d).execute()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doDropboxRestore(e: StartDropboxRestore) {
        val d = ProgressDialog.show(this, null, getString(R.string.dropbox_loading_files), true)
        progressDialog = d
        DropboxListFilesTask(this, d).execute()
    }

    class StartDropboxBackup
    class StartDropboxRestore
    class StartDriveBackup
    class StartDriveRestore

    companion object {
        const val RESOLVE_CONNECTION_REQUEST_CODE: Int = 1
    }
}
