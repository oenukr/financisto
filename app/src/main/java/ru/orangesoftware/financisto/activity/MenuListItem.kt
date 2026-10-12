package ru.orangesoftware.financisto.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.widget.ListAdapter
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.RequestPermission.isRequestingPermission
import ru.orangesoftware.financisto.activity.RequestPermission.isRequestingPermissions
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.backup.Backup
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.export.BackupExportTask
import ru.orangesoftware.financisto.export.BackupImportTask
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.export.ImportExportAsyncTaskListener
import ru.orangesoftware.financisto.export.csv.CsvExportOptions
import ru.orangesoftware.financisto.export.csv.CsvExportTask
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportTask
import ru.orangesoftware.financisto.export.qif.QifExportOptions
import ru.orangesoftware.financisto.export.qif.QifExportTask
import ru.orangesoftware.financisto.export.qif.QifImportOptions
import ru.orangesoftware.financisto.export.qif.QifImportTask
import ru.orangesoftware.financisto.utils.EntityEnum
import ru.orangesoftware.financisto.utils.EnumUtils
import ru.orangesoftware.financisto.utils.EnumUtils.showPickOneDialog
import ru.orangesoftware.financisto.utils.ExecutableEntityEnum
import ru.orangesoftware.financisto.utils.IntegrityFix
import ru.orangesoftware.financisto.utils.SummaryEntityEnum

enum class MenuListItem(
    override val titleId: Int,
    private val summaryResId: Int,
    override val iconId: Int,
) : SummaryEntityEnum {

    MENU_PREFERENCES(R.string.preferences, R.string.preferences_summary, R.drawable.drawer_action_preferences) {
        override fun call(activity: Activity) {
            activity.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_CHANGE_PREFERENCES)
        }
    },
    MENU_ENTITIES(R.string.entities, R.string.entities_summary, R.drawable.drawer_action_entities) {
        override fun call(activity: Activity) {
            val entities = MenuEntities.values()
            val adapter: ListAdapter = EnumUtils.createEntityEnumAdapter(activity, entities)
            val d = AlertDialog.Builder(activity)
                .setAdapter(adapter) { dialog, which ->
                    dialog.dismiss()
                    val e = entities[which]
                    val permissions = e.permissions
                    if (permissions.isEmpty() || !isRequestingPermissions(activity, *permissions)) {
                        activity.startActivity(Intent(activity, e.activityClass))
                    }
                }
                .create()
            d.setTitle(R.string.entities)
            d.show()
        }
    },
    MENU_BACKUP(R.string.backup_database, R.string.backup_database_summary, R.drawable.actionbar_db_backup) {
        override fun call(activity: Activity) {
            if (isRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return
            }
            val d = ProgressDialog.show(activity, null, activity.getString(R.string.backup_database_inprogress), true)
            BackupExportTask(activity, d, true).execute()
        }
    },
    MENU_RESTORE(R.string.restore_database, R.string.restore_database_summary, R.drawable.actionbar_db_restore) {
        override fun call(activity: Activity) {
            if (isRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return
            }
            val backupFiles = Backup.listBackups(activity)
            val backupFilesNames = Array(backupFiles.size) { i -> backupFiles[i].name }
            val selectedBackupFile = arrayOfNulls<String>(1)
            AlertDialog.Builder(activity)
                .setTitle(R.string.restore_database)
                .setPositiveButton(R.string.restore) { _, _ ->
                    val file = selectedBackupFile[0]
                    if (file != null) {
                        val d = ProgressDialog.show(activity, null, activity.getString(R.string.restore_database_inprogress), true)
                        BackupImportTask(activity, d).execute(file)
                    }
                }
                .setSingleChoiceItems(backupFilesNames, -1) { _, which ->
                    if (which in backupFiles.indices) {
                        selectedBackupFile[0] = backupFilesNames[which]
                    }
                }
                .show()
        }
    },
    DROPBOX_BACKUP(R.string.backup_database_online_dropbox, R.string.backup_database_online_dropbox_summary, R.drawable.actionbar_dropbox) {
        override fun call(activity: Activity) {
            if (isRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return
            }
            DependenciesHolder().greenRobotBus.post(MenuListActivity.StartDropboxBackup())
        }
    },
    DROPBOX_RESTORE(R.string.restore_database_online_dropbox, R.string.restore_database_online_dropbox_summary, R.drawable.actionbar_dropbox) {
        override fun call(activity: Activity) {
            if (isRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return
            }
            DependenciesHolder().greenRobotBus.post(MenuListActivity.StartDropboxRestore())
        }
    },
    MENU_BACKUP_TO(R.string.backup_database_to, R.string.backup_database_to_summary, R.drawable.actionbar_share) {
        override fun call(activity: Activity) {
            if (isRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return
            }
            val d = ProgressDialog.show(activity, null, activity.getString(R.string.backup_database_inprogress), true)
            val t = BackupExportTask(activity, d, false)
            t.setShowResultMessage(false)
            t.setListener(ImportExportAsyncTaskListener { _ ->
                val backupFileName = t.backupFileName
                if (backupFileName != null) {
                    val file = Export.getBackupFile(activity, backupFileName)
                    val intent = Intent(Intent.ACTION_SEND)
                    val backupFileUri: Uri = file.uri
                    intent.putExtra(Intent.EXTRA_STREAM, backupFileUri)
                    intent.type = "text/plain"
                    activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.backup_database_to_title)))
                }
            })
            t.execute()
        }
    },
    MENU_IMPORT_EXPORT(R.string.import_export, R.string.import_export_summary, R.drawable.actionbar_export) {
        override fun call(activity: Activity) {
            if (isRequestingPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return
            }
            showPickOneDialog(activity, R.string.import_export, ImportExportEntities.values(), activity)
        }
    },
    MENU_MASS_OP(R.string.mass_operations, R.string.mass_operations_summary, R.drawable.ic_menu_agenda) {
        override fun call(activity: Activity) {
            activity.startActivity(Intent(activity, MassOpActivity::class.java))
        }
    },
    MENU_SCHEDULED_TRANSACTIONS(R.string.scheduled_transactions, R.string.scheduled_transactions_summary, R.drawable.actionbar_calendar) {
        override fun call(activity: Activity) {
            activity.startActivity(Intent(activity, ScheduledListActivity::class.java))
        }
    },
    MENU_PLANNER(R.string.planner, R.string.planner_summary, R.drawable.actionbar_calendar) {
        override fun call(activity: Activity) {
            activity.startActivity(Intent(activity, PlannerActivity::class.java))
        }
    },
    MENU_PERMISSIONS(R.string.permissions, R.string.permissions_summary, R.drawable.ic_tab_about) {
        override fun call(activity: Activity) {
            RequestPermissionActivity.intent(activity).start()
        }
    },
    MENU_INTEGRITY_FIX(R.string.integrity_fix, R.string.integrity_fix_summary, R.drawable.actionbar_flash) {
        override fun call(activity: Activity) {
            doIntegrityFix(activity)
        }
    },
    MENU_DONATE(R.string.donate, R.string.donate_summary, R.drawable.actionbar_donate) {
        override fun call(activity: Activity) {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=pname:ru.orangesoftware.financisto.support"),
                )
                activity.startActivity(browserIntent)
            } catch (ex: Exception) {
                Toast.makeText(activity, R.string.donate_error, Toast.LENGTH_LONG).show()
            }
        }
    },
    MENU_ABOUT(R.string.about, R.string.about_summary, R.drawable.ic_action_info) {
        override fun call(activity: Activity) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }
    };

    override fun getSummaryId(): Int = summaryResId

    abstract fun call(activity: Activity)

    private enum class MenuEntities(
        override val titleId: Int,
        override val iconId: Int,
        val activityClass: Class<*>,
        vararg val permissions: String,
    ) : EntityEnum {
        CURRENCIES(R.string.currencies, R.drawable.ic_action_money, CurrencyListActivity::class.java),
        EXCHANGE_RATES(R.string.exchange_rates, R.drawable.ic_action_line_chart, ExchangeRatesListActivity::class.java),
        CATEGORIES(R.string.categories, R.drawable.ic_action_category, CategoryListActivity2::class.java),
        SMS_TEMPLATES(R.string.sms_templates, R.drawable.ic_action_sms, SmsDragListActivity::class.java, Manifest.permission.RECEIVE_SMS),
        PAYEES(R.string.payees, R.drawable.ic_action_users, PayeeListActivity::class.java),
        PROJECTS(R.string.projects, R.drawable.ic_action_gear, ProjectListActivity::class.java),
        LOCATIONS(R.string.locations, R.drawable.ic_action_location_2, LocationsListActivity::class.java);
    }

    private enum class ImportExportEntities(
        override val titleId: Int,
        override val iconId: Int,
    ) : ExecutableEntityEnum<Activity> {
        CSV_EXPORT(R.string.csv_export, R.drawable.backup_csv) {
            override fun execute(value: Activity) {
                val intent = Intent(value, CsvExportActivity::class.java)
                value.startActivityForResult(intent, ACTIVITY_CSV_EXPORT)
            }
        },
        CSV_IMPORT(R.string.csv_import, R.drawable.backup_csv) {
            override fun execute(value: Activity) {
                val intent = Intent(value, CsvImportActivity::class.java)
                value.startActivityForResult(intent, ACTIVITY_CSV_IMPORT)
            }
        },
        QIF_EXPORT(R.string.qif_export, R.drawable.backup_qif) {
            override fun execute(value: Activity) {
                val intent = Intent(value, QifExportActivity::class.java)
                value.startActivityForResult(intent, ACTIVITY_QIF_EXPORT)
            }
        },
        QIF_IMPORT(R.string.qif_import, R.drawable.backup_qif) {
            override fun execute(value: Activity) {
                val intent = Intent(value, QifImportActivity::class.java)
                value.startActivityForResult(intent, ACTIVITY_QIF_IMPORT)
            }
        };
    }

    companion object {
        const val ACTIVITY_CSV_EXPORT: Int = 2
        const val ACTIVITY_QIF_EXPORT: Int = 3
        const val ACTIVITY_CSV_IMPORT: Int = 4
        const val ACTIVITY_QIF_IMPORT: Int = 5
        const val ACTIVITY_CHANGE_PREFERENCES: Int = 6

        @JvmStatic
        fun doCsvExport(activity: Activity, options: CsvExportOptions) {
            val progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.csv_export_inprogress), true)
            CsvExportTask(activity, progressDialog, options).execute()
        }

        @JvmStatic
        fun doCsvImport(activity: Activity, options: CsvImportOptions) {
            val progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.csv_import_inprogress), true)
            CsvImportTask(activity, progressDialog, options).execute()
        }

        @JvmStatic
        fun doQifExport(activity: Activity, options: QifExportOptions) {
            val progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.qif_export_inprogress), true)
            QifExportTask(activity, progressDialog, options).execute()
        }

        @JvmStatic
        fun doQifImport(activity: Activity, options: QifImportOptions) {
            val progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.qif_import_inprogress), true)
            QifImportTask(activity, progressDialog, options).execute()
        }

        private fun doIntegrityFix(activity: Activity) {
            val progressDialog = ProgressDialog.show(activity, null, activity.getString(R.string.integrity_fix_in_progress), true)
            val appContext = activity.applicationContext
            DependenciesHolder().applicationScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        IntegrityFix(DatabaseAdapter(appContext)).fix()
                    }
                    if (activity is MainActivity && !activity.isFinishing && !activity.isDestroyed) {
                        activity.refreshCurrentTab()
                    }
                } finally {
                    if (!activity.isDestroyed) progressDialog.dismiss()
                }
            }
        }
    }
}
