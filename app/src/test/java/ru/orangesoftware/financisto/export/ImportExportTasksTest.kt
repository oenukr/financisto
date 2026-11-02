// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.export

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import ru.orangesoftware.financisto.bus.RefreshCurrentTab
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.csv.CsvExportOptions
import ru.orangesoftware.financisto.export.csv.CsvExportTask
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportTask
import ru.orangesoftware.financisto.export.dropbox.DropboxBackupTask
import ru.orangesoftware.financisto.export.dropbox.DropboxListFilesTask
import ru.orangesoftware.financisto.export.dropbox.DropboxRestoreTask
import ru.orangesoftware.financisto.export.qif.QifDateFormat
import ru.orangesoftware.financisto.export.qif.QifExportOptions
import ru.orangesoftware.financisto.export.qif.QifExportTask
import ru.orangesoftware.financisto.export.qif.QifImportOptions
import ru.orangesoftware.financisto.export.qif.QifImportTask
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("DEPRECATION")
class ImportExportTasksTest : AbstractDbTest() {

    private lateinit var activity: Activity
    private lateinit var progressDialog: ProgressDialog
    private lateinit var defaultCurrency: Currency
    private lateinit var defaultAccount: Account

    @Before
    override fun setUp() {
        super.setUp()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        progressDialog = ProgressDialog(activity)

        defaultCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .separators("','", "'.'")
            .makeDefault()
            .create()

        defaultAccount = AccountBuilder.createDefault(db, defaultCurrency)
        CurrencyCache.initialize(db)
    }

    private class ConcreteTestTask(
        context: Activity,
        dialog: ProgressDialog,
        private val workBlock: (Context, DatabaseAdapter) -> Any?
    ) : ImportExportAsyncTask(context, dialog) {

        fun callPublishProgress(value: String) {
            publishProgress(value)
        }

        fun callDoUploadToDropbox(ctx: Context, file: String) {
            doUploadToDropbox(ctx, file)
        }

        override suspend fun work(context: Context, db: DatabaseAdapter, vararg params: String?): Any? =
            workBlock(context, db)

        override fun getSuccessMessage(result: Any?): String = "SuccessMessage: $result"
    }

    private fun ImportExportAsyncTask.callGetSuccessMessage(result: Any?): String? {
        var clazz: Class<*>? = javaClass
        while (clazz != null) {
            try {
                val method = clazz.declaredMethods.firstOrNull { it.name == "getSuccessMessage" }
                if (method != null) {
                    method.isAccessible = true
                    return method.invoke(this, result) as? String
                }
            } catch (_: Exception) {
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun getLatestErrorDialog(): AlertDialog? =
        ShadowAlertDialog.getShownDialogs()
            .filterIsInstance<AlertDialog>()
            .lastOrNull { shadowOf(it).title == activity.getString(R.string.fail) }

    @Test
    fun baseTask_lifecycle_opensAndClosesDatabase_dismissesDialog_andNotifiesListener() {
        progressDialog.show()
        assertTrue(progressDialog.isShowing)

        var dbOpenDuringWork = false
        var listenerResult: Any? = null
        var refreshEventReceived = false

        val bus = DependenciesHolder().greenRobotBus
        val subscriber = object {
            @Subscribe
            fun onRefresh(ignored: RefreshCurrentTab) {
                refreshEventReceived = true
            }
        }
        bus.register(subscriber)

        try {
            val task = ConcreteTestTask(activity, progressDialog) { _, workerDb ->
                dbOpenDuringWork = workerDb.db().isOpen
                "TestResult123"
            }
            task.setListener { result ->
                listenerResult = result
            }

            task.execute()
            task.get()
            ShadowLooper.idleMainLooper()

            assertTrue(dbOpenDuringWork)
            assertFalse(progressDialog.isShowing)
            assertEquals("TestResult123", listenerResult)
            assertTrue(refreshEventReceived)
            val toast = ShadowToast.getTextOfLatestToast()
            assertTrue(toast?.contains("SuccessMessage: TestResult123") == true)
        } finally {
            bus.unregister(subscriber)
        }
    }

    @Test
    fun baseTask_progressUpdates_modifiesDialogMessage() {
        progressDialog.show()
        val task = ConcreteTestTask(activity, progressDialog) { _, _ -> "done" }

        task.callPublishProgress("Importing 45%...")
        ShadowLooper.idleMainLooper()

        assertEquals("Importing 45%...", shadowOf(progressDialog).message?.toString())
    }

    @Test
    fun baseTask_whenImportExportExceptionThrown_showsErrorAlertDialog() {
        progressDialog.show()
        val cause = IllegalArgumentException("File corrupted")
        val task = ConcreteTestTask(activity, progressDialog) { _, _ ->
            throw ImportExportException(R.string.import_file_not_found, cause)
        }

        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        assertFalse(progressDialog.isShowing)
        val alert = getLatestErrorDialog()
        assertNotNull("Expected alert dialog to be displayed on ImportExportException", alert)
        val shadowAlert = shadowOf(requireNotNull(alert))
        assertEquals(activity.getString(R.string.fail), shadowAlert.title)
        assertTrue(shadowAlert.message.contains(activity.getString(R.string.import_file_not_found)))
        assertTrue(shadowAlert.message.contains("File corrupted"))
    }

    @Test
    fun baseTask_whenGenericExceptionThrown_doesNotShowSuccessOrAlert() {
        progressDialog.show()
        val task = ConcreteTestTask(activity, progressDialog) { _, _ ->
            throw IllegalStateException("Generic unexpected database error")
        }

        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        assertFalse(progressDialog.isShowing)
        assertNull(getLatestErrorDialog())
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun baseTask_whenShowResultMessageFalse_doesNotShowToast() {
        progressDialog.show()
        val task = ConcreteTestTask(activity, progressDialog) { _, _ -> "OK" }
        task.setShowResultMessage(false)

        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun baseTask_doUploadToDropbox_skippedWhenPreferenceDisabled() {
        MyPreferences.removeDropboxKeys(activity)
        val task = ConcreteTestTask(activity, progressDialog) { _, _ -> "OK" }
        task.callDoUploadToDropbox(activity, "dummy_file.backup")
    }

    @Test
    fun backupExportTask_exportsDatabaseAndReturnsFilename() {
        progressDialog.show()
        TransactionBuilder.withDb(db)
            .account(defaultAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(5000L)
            .create()

        val task = BackupExportTask(activity, progressDialog, false)
        task.execute()
        val filename = task.get()
        ShadowLooper.idleMainLooper()

        assertNotNull(filename)
        assertTrue(filename.toString().endsWith(".backup"))
        assertEquals(filename, task.backupFileName)
        assertEquals(filename.toString(), task.callGetSuccessMessage(filename))
        assertTrue(File(Export.DEFAULT_EXPORT_PATH, filename.toString()).exists())
    }

    @Test
    fun backupImportTask_importsDatabaseFromFile() {
        progressDialog.show()
        val exportTask = BackupExportTask(activity, progressDialog, false)
        exportTask.execute()
        val backupFile = exportTask.get() as String
        ShadowLooper.idleMainLooper()

        val backupOnDisk = File(Export.DEFAULT_EXPORT_PATH, backupFile)
        registerFileForContentResolver(backupOnDisk)

        val importTask = BackupImportTask(activity, progressDialog)
        importTask.execute(backupFile)
        val result = importTask.get()
        ShadowLooper.idleMainLooper()

        assertEquals(true, result)
        assertEquals(activity.getString(R.string.restore_database_success), importTask.callGetSuccessMessage(result))
    }

    @Test
    fun csvExportTask_exportsCsvFileAndReturnsFilename() {
        progressDialog.show()
        TransactionBuilder.withDb(db)
            .account(defaultAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(3000L)
            .create()

        val options = CsvExportOptions(
            CurrencyCache.createCurrencyFormat(defaultCurrency),
            ',',
            true,
            false,
            false,
            WhereFilter.empty(),
            true
        )
        val task = CsvExportTask(activity, progressDialog, options)
        task.execute()
        val filename = task.get()
        ShadowLooper.idleMainLooper()

        assertNotNull(filename)
        assertTrue(filename.toString().endsWith(".csv"))
        assertEquals(filename.toString(), task.callGetSuccessMessage(filename))
        assertTrue(File(Export.DEFAULT_EXPORT_PATH, filename.toString()).exists())
    }

    @Test
    fun qifExportTask_exportsQifFileAndReturnsFilename() {
        progressDialog.show()
        TransactionBuilder.withDb(db)
            .account(defaultAccount)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(4500L)
            .create()

        val options = QifExportOptions(
            defaultCurrency,
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            null,
            WhereFilter.empty(),
            false
        )
        val task = QifExportTask(activity, progressDialog, options)
        task.execute()
        val filename = task.get()
        ShadowLooper.idleMainLooper()

        assertNotNull(filename)
        assertTrue(filename.toString().endsWith(".qif"))
        assertEquals(filename.toString(), task.callGetSuccessMessage(filename))
        assertTrue(File(Export.DEFAULT_EXPORT_PATH, filename.toString()).exists())
    }

    @Test
    fun qifImportTask_whenFileMissing_showsQifImportErrorDialog() {
        progressDialog.show()
        val options = QifImportOptions(
            "non_existent_file.qif",
            QifDateFormat.EU_FORMAT,
            defaultCurrency
        )
        val task = QifImportTask(activity, progressDialog, options)
        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        val alert = getLatestErrorDialog()
        assertNotNull(alert)
        val shadowAlert = shadowOf(requireNotNull(alert))
        assertTrue(shadowAlert.message.contains(activity.getString(R.string.qif_import_error)))
        assertEquals(activity.getString(R.string.qif_import_success), task.callGetSuccessMessage(null))
    }

    @Test
    fun csvImportTask_whenFileMissing_showsErrorDialog() {
        progressDialog.show()
        val options = CsvImportOptions(
            defaultCurrency,
            SimpleDateFormat("dd.MM.yyyy", Locale.US),
            defaultAccount.id,
            WhereFilter.empty(),
            "file:///non_existent_folder/dummy.csv",
            ',',
            true
        )
        val task = CsvImportTask(activity, progressDialog, options)
        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        val alert = getLatestErrorDialog()
        assertNotNull(alert)
        val shadowAlert = shadowOf(requireNotNull(alert))
        assertTrue(
            shadowAlert.message.contains(activity.getString(R.string.import_file_not_found_2, "file:"))
        )
        assertEquals("SampleResult", task.callGetSuccessMessage("SampleResult"))
    }

    @Test
    fun csvImportTask_importsTransactionsSuccessfully() {
        progressDialog.show()
        val tmp = File.createTempFile("test_import", ".csv")
        tmp.writeText(
            "date,time,account,amount,currency,category,parent,payee,location,project,note\n" +
                "10.07.2026,07:13:17,Dollar,-10.50,USD,,,,,,note\n"
        )
        registerFileForContentResolver(tmp)

        val options = CsvImportOptions(
            defaultCurrency,
            SimpleDateFormat("dd.MM.yyyy", Locale.US),
            defaultAccount.id,
            WhereFilter.empty(),
            android.net.Uri.fromFile(tmp).toString(),
            ',',
            true
        )
        val task = CsvImportTask(activity, progressDialog, options)
        task.execute()
        val result = task.get()
        ShadowLooper.idleMainLooper()

        assertNotNull(result)
        assertTrue(result.toString().contains("imported!"))
        assertEquals(result.toString(), task.callGetSuccessMessage(result))
    }

    @Test
    fun dropboxBackupTask_whenUnauthenticated_showsAuthErrorDialog() {
        progressDialog.show()
        MyPreferences.removeDropboxKeys(activity)

        val task = DropboxBackupTask(activity, progressDialog)
        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        val alert = getLatestErrorDialog()
        assertNotNull(alert)
        val shadowAlert = shadowOf(requireNotNull(alert))
        assertTrue(shadowAlert.message.contains(activity.getString(R.string.dropbox_error)))
        assertEquals("test_file.backup", task.callGetSuccessMessage("test_file.backup"))
    }

    @Test
    fun dropboxRestoreTask_whenUnauthenticated_showsAuthErrorDialog() {
        progressDialog.show()
        MyPreferences.removeDropboxKeys(activity)

        val task = DropboxRestoreTask(activity, progressDialog, "dummy.backup")
        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        val alert = getLatestErrorDialog()
        assertNotNull(alert)
        val shadowAlert = shadowOf(requireNotNull(alert))
        assertTrue(shadowAlert.message.contains(activity.getString(R.string.dropbox_auth_error)))
        assertEquals(activity.getString(R.string.restore_database_success), task.callGetSuccessMessage(true))
    }

    @Test
    fun dropboxListFilesTask_whenUnauthenticated_showsDropboxErrorDialog() {
        progressDialog.show()
        MyPreferences.removeDropboxKeys(activity)

        val task = DropboxListFilesTask(activity, progressDialog)
        task.execute()
        task.get()
        ShadowLooper.idleMainLooper()

        val alert = getLatestErrorDialog()
        assertNotNull(alert)
        val shadowAlert = shadowOf(requireNotNull(alert))
        assertTrue(shadowAlert.message.contains(activity.getString(R.string.dropbox_error)))
        assertNull(task.callGetSuccessMessage(emptyArray<String>()))
    }
}
