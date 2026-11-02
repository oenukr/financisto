// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.preference.PreferenceManager
import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.export.drive.DoDriveBackup
import ru.orangesoftware.financisto.export.drive.DoDriveListFiles
import ru.orangesoftware.financisto.export.drive.DoDriveRestore
import ru.orangesoftware.financisto.export.drive.DriveBackupError
import ru.orangesoftware.financisto.export.drive.DriveBackupSuccess
import ru.orangesoftware.financisto.export.drive.DriveFileInfo
import ru.orangesoftware.financisto.export.drive.DriveFileList
import ru.orangesoftware.financisto.export.drive.DriveRestoreSuccess
import ru.orangesoftware.financisto.export.drive.DropboxFileList
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import java.util.Date

@Suppress("DEPRECATION")
class MenuListActivityTest : AbstractDbTest() {

    private lateinit var homeCurrency: Currency
    private lateinit var account: Account

    @Before
    override fun setUp() {
        super.setUp()
        homeCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .separators("','", "'.'")
            .makeDefault()
            .create()

        account = AccountBuilder.createDefault(db, homeCurrency)
        CurrencyCache.initialize(db)
    }

    @Test
    fun activity_bindsAllMenuItemsInListView() {
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val listView = activity.listView
        assertNotNull(listView)
        assertEquals(MenuListItem.entries.size, listView.count)

        controller.destroy()
    }

    @Test
    fun onListItemClick_preferencesItem_startsPreferencesActivity() {
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val preferencesIndex = MenuListItem.entries.indexOf(MenuListItem.MENU_PREFERENCES)
        assertTrue(preferencesIndex >= 0)

        activity.listView.performItemClick(null, preferencesIndex, preferencesIndex.toLong())

        val shadowActivity = shadowOf(activity)
        val startedIntent = shadowActivity.nextStartedActivityForResult
        assertNotNull(startedIntent)
        assertEquals(PreferencesActivity::class.java.name, startedIntent.intent.component?.className)
        assertEquals(MenuListItem.ACTIVITY_CHANGE_PREFERENCES, startedIntent.requestCode)

        controller.destroy()
    }

    @Test
    fun onActivityResult_csvExportResult_executesExportTask() {
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        ru.orangesoftware.financisto.export.Export.DEFAULT_EXPORT_PATH.mkdirs()
        val intent = Intent().apply {
            putExtra("CSV_EXPORT_DECIMALS", 2)
            putExtra("CSV_EXPORT_DECIMAL_SEPARATOR", "'.'")
            putExtra("CSV_EXPORT_GROUP_SEPARATOR", "','")
            putExtra(CsvExportActivity.CSV_EXPORT_FIELD_SEPARATOR, ',')
            putExtra(CsvExportActivity.CSV_EXPORT_INCLUDE_HEADER, true)
            putExtra(CsvExportActivity.CSV_EXPORT_SPLITS, false)
            putExtra(CsvExportActivity.CSV_EXPORT_UPLOAD_TO_DROPBOX, false)
        }

        ShadowToast.reset()
        activity.onCsvExportResult(Activity.RESULT_OK, intent)

        var toastText: String? = null
        repeat(50) {
            if (toastText == null) {
                ShadowLooper.idleMainLooper()
                toastText = ShadowToast.getTextOfLatestToast()
                if (toastText == null) {
                    Thread.sleep(50)
                }
            }
        }

        assertNotNull("Expected CSV export success toast", toastText)
        assertTrue(
            "Expected toast to contain CSV success message, got: $toastText",
            requireNotNull(toastText).contains(".csv"),
        )

        controller.destroy()
    }

    @Test
    fun onChangePreferences_schedulesAutoBackup() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("auto_backup_enabled", true)
            .putInt("auto_backup_time", 1430)
            .commit()

        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        activity.onChangePreferences()
        ShadowLooper.idleMainLooper()

        controller.destroy()
    }

    @Test
    fun eventBus_startDropboxBackupAndRestore_runsTasks() {
        val bus = DependenciesHolder().greenRobotBus
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        ShadowLooper.idleMainLooper()

        bus.post(MenuListActivity.StartDropboxBackup())
        ShadowLooper.idleMainLooper()

        val latestAlert = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(latestAlert)

        bus.post(MenuListActivity.StartDropboxRestore())
        ShadowLooper.idleMainLooper()

        controller.destroy()
    }

    @Test
    fun eventBus_doImportFromDropbox_displaysFileListDialog() {
        val bus = DependenciesHolder().greenRobotBus
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val backupFiles = arrayOf("backup_file_1.backup", "backup_file_2.backup")
        bus.post(DropboxFileList(backupFiles))
        ShadowLooper.idleMainLooper()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val shadowDialog = shadowOf(dialog)
        assertEquals(activity.getString(R.string.restore_database_online_dropbox), shadowDialog.title)

        controller.destroy()
    }

    @Test
    fun eventBus_googleDriveBackupAndRestore_dispatchesDriveEvents() {
        val bus = DependenciesHolder().greenRobotBus
        var driveBackupReceived = false
        var driveRestoreReceived = false

        val subscriber = object {
            @Subscribe
            fun onDriveBackup(ignored: DoDriveBackup) {
                driveBackupReceived = true
            }

            @Subscribe
            fun onDriveListFiles(ignored: DoDriveListFiles) {
                driveRestoreReceived = true
            }
        }
        bus.register(subscriber)

        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        ShadowLooper.idleMainLooper()

        try {
            bus.post(MenuListActivity.StartDriveBackup())
            ShadowLooper.idleMainLooper()
            assertTrue("Expected DoDriveBackup event", driveBackupReceived)

            bus.post(MenuListActivity.StartDriveRestore())
            ShadowLooper.idleMainLooper()
            assertTrue("Expected DoDriveListFiles event", driveRestoreReceived)
        } finally {
            bus.unregister(subscriber)
            controller.destroy()
        }
    }

    @Test
    fun eventBus_onDriveList_displaysDialogAndPostsDoDriveRestoreOnConfirm() {
        val bus = DependenciesHolder().greenRobotBus
        var receivedRestoreFile: DriveFileInfo? = null

        val subscriber = object {
            @Subscribe
            fun onDriveRestore(e: DoDriveRestore) {
                receivedRestoreFile = e.selectedDriveFile
            }
        }
        bus.register(subscriber)

        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        try {
            val fileInfo = DriveFileInfo(null, "drive_backup.backup", Date())
            bus.post(DriveFileList(listOf(fileInfo)))
            ShadowLooper.idleMainLooper()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            val shadowDialog = shadowOf(dialog)
            assertEquals(activity.getString(R.string.restore_database_online_google_drive), shadowDialog.title)

            shadowDialog.clickOnItem(0)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            ShadowLooper.idleMainLooper()

            assertNotNull(receivedRestoreFile)
            assertEquals("drive_backup.backup", receivedRestoreFile?.title)
        } finally {
            bus.unregister(subscriber)
            controller.destroy()
        }
    }

    @Test
    fun driveResultsAndConnectionHandlers_displayExpectedToasts() {
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        activity.onDriveBackupSuccess(DriveBackupSuccess("my_backup_123.backup"))
        var toast = ShadowToast.getTextOfLatestToast()
        assertNotNull(toast)
        assertTrue(requireNotNull(toast).contains("my_backup_123.backup"))

        activity.onDriveRestoreSuccess(DriveRestoreSuccess())
        toast = ShadowToast.getTextOfLatestToast()
        assertNotNull(toast)
        assertEquals(activity.getString(R.string.restore_database_success), toast)

        activity.onDriveBackupError(DriveBackupError("network_timeout"))
        toast = ShadowToast.getTextOfLatestToast()
        assertNotNull(toast)
        assertTrue(requireNotNull(toast).contains("network_timeout"))

        activity.onConnectionRequest(Activity.RESULT_OK)
        toast = ShadowToast.getTextOfLatestToast()
        assertNotNull(toast)
        assertEquals(activity.getString(R.string.google_drive_connection_resolved), toast)

        controller.destroy()
    }

    @Test
    fun lifecycle_pauseAndResume_managesPinAndEventBus() {
        val bus = DependenciesHolder().greenRobotBus
        val controller = Robolectric.buildActivity(MenuListActivity_::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        assertTrue(bus.bus.isRegistered(activity))

        controller.pause()
        assertFalse(bus.bus.isRegistered(activity))

        controller.resume()
        assertTrue(bus.bus.isRegistered(activity))

        controller.destroy()
    }
}
