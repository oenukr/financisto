package ru.orangesoftware.financisto.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import ru.orangesoftware.financisto.db.DatabaseHelper.ACCOUNT_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.ATTRIBUTES_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.BUDGET_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.CATEGORY_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.CCARD_CLOSING_DATE_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.CURRENCY_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.EXCHANGE_RATES_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.LOCATIONS_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.PAYEE_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.PROJECT_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.SMS_TEMPLATES_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE
import ru.orangesoftware.financisto.db.DatabaseHelper.TRANSACTION_TABLE
import ru.orangesoftware.financisto.export.Export

object Backup {

    @JvmField
    val BACKUP_TABLES: Array<String> = arrayOf(
        ACCOUNT_TABLE, ATTRIBUTES_TABLE, CATEGORY_ATTRIBUTE_TABLE,
        TRANSACTION_ATTRIBUTE_TABLE, BUDGET_TABLE, CATEGORY_TABLE,
        CURRENCY_TABLE, LOCATIONS_TABLE, PROJECT_TABLE, TRANSACTION_TABLE,
        PAYEE_TABLE, CCARD_CLOSING_DATE_TABLE, SMS_TEMPLATES_TABLE,
        "split", /* todo: seems not used, found only in old 20110422_0051_create_split_table.sql, should be removed then */
        EXCHANGE_RATES_TABLE,
    )

    @JvmField
    val BACKUP_TABLES_WITH_SYSTEM_IDS: Array<String> = arrayOf(
        ATTRIBUTES_TABLE,
        CATEGORY_TABLE,
        PROJECT_TABLE,
        LOCATIONS_TABLE,
    )

    @JvmField
    val BACKUP_TABLES_WITH_SORT_ORDER: Array<String> = arrayOf(
        ACCOUNT_TABLE,
        SMS_TEMPLATES_TABLE,
        PROJECT_TABLE,
        PAYEE_TABLE,
        BUDGET_TABLE,
        CURRENCY_TABLE,
        LOCATIONS_TABLE,
        ATTRIBUTES_TABLE,
    )

    @JvmField
    val RESTORE_SCRIPTS: Array<String> = arrayOf(
            "20100114_1158_alter_accounts_types.sql",
            "20110903_0129_alter_template_splits.sql",
            "20171230_1852_alter_electronic_account_type.sql",
    )

    @JvmStatic
    fun listBackups(context: Context): Array<DocumentFile> = Export
        .getBackupFolder(context)
        .listFiles()
        .filter { documentFile ->
            documentFile.name?.endsWith(".backup") == true
        }
        .sortedBy { documentFile ->
            documentFile.name
        }
        .toTypedArray()

    @JvmStatic
    fun tableHasSystemIds(tableName: String): Boolean {
        BACKUP_TABLES_WITH_SYSTEM_IDS.forEach { table ->
            if (table.equals(tableName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun tableHasOrder(tableName: String): Boolean {
        BACKUP_TABLES_WITH_SORT_ORDER.forEach { table ->
            if (table.equals(tableName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
