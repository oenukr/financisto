package ru.orangesoftware.financisto.activity

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.backup.DatabaseExport
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.utils.Logger
import java.text.DateFormat
import java.util.Calendar

class PurgeAccountActivity : AbstractActivity() {

    private val logger: Logger = DependenciesHolder().logger

    private var account: Account? = null
    private lateinit var date: Calendar

    private lateinit var layout: LinearLayout
    private lateinit var databaseBackup: CheckBox
    private lateinit var dateText: TextView
    private lateinit var df: DateFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.purge_account)

        df = DateUtils.getLongDateFormat(this)

        layout = findViewById(R.id.layout)
        date = Calendar.getInstance().apply {
            add(Calendar.YEAR, -1)
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val bOk = findViewById<Button>(R.id.bOK)
        bOk.setOnClickListener { deleteOldTransactions() }

        val bCancel = findViewById<Button>(R.id.bCancel)
        bCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        if (loadAccount()) {
            createNodes()
            setDateText()
        }
    }

    private fun deleteOldTransactions() {
        val currentAccount = account ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.purge_account_confirm_title)
            .setMessage(getString(R.string.purge_account_confirm_message, currentAccount.title, getDateString()))
            .setPositiveButton(R.string.ok) { _, _ -> purgeAccount() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadAccount(): Boolean {
        val currentIntent = intent
        if (currentIntent == null) {
            Toast.makeText(this, "No account specified", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        val accountId = currentIntent.getLongExtra(ACCOUNT_ID, -1)
        if (accountId <= 0) {
            Toast.makeText(this, "Invalid account specified", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        val loadedAccount = db.getAccount(accountId)
        if (loadedAccount == null) {
            Toast.makeText(this, "No account found", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        account = loadedAccount
        return true
    }

    private fun createNodes() {
        val currentAccount = account ?: return
        activityLayout.addInfoNode(layout, 0, R.string.account, currentAccount.title)
        activityLayout.addInfoNode(layout, 0, R.string.warning, R.string.purge_account_date_summary)
        dateText = activityLayout.addInfoNode(layout, R.id.date, R.string.date, "?")
        databaseBackup = activityLayout.addCheckboxNode(
            layout,
            R.id.backup,
            R.string.database_backup,
            R.string.purge_account_backup_database,
            true,
        )
    }

    override fun onClick(v: View, id: Int) {
        if (id == R.id.date) {
            val dialog = DatePickerDialog.newInstance(
                { _, year, monthOfYear, dayOfMonth ->
                    date.set(year, monthOfYear, dayOfMonth)
                    setDateText()
                },
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH),
                date.get(Calendar.DAY_OF_MONTH),
            )
            UiUtils.applyTheme(this, dialog)
            dialog.show(supportFragmentManager, "DatePickerDialog")
        } else if (id == R.id.backup) {
            databaseBackup.isChecked = !databaseBackup.isChecked
        }
    }

    private fun setDateText() {
        dateText.text = getDateString()
    }

    private fun getDateString(): String = df.format(date.time)

    @Suppress("TooGenericExceptionCaught")
    private fun purgeAccount() {
        val currentAccount = account ?: return
        val databaseBackupChecked = databaseBackup.isChecked
        val targetDateMillis = date.timeInMillis
        val appContext = applicationContext
        val d: Dialog = ProgressDialog.show(
            this,
            null,
            getString(R.string.purge_account_in_progress),
            true,
        )

        lifecycleScope.launch {
            val error = try {
                withContext(Dispatchers.IO + NonCancellable) {
                    if (databaseBackupChecked) {
                        val export = DatabaseExport(appContext, db.db(), true)
                        export.export()
                    }
                    db.purgeAccountAtDate(currentAccount, targetDateMillis)
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e, "Unexpected error")
                e
            } finally {
                if (!isFinishing && !isDestroyed) {
                    d.dismiss()
                }
            }

            if (error != null) {
                Toast.makeText(
                    this@PurgeAccountActivity,
                    R.string.purge_account_unable_to_do_backup,
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        const val ACCOUNT_ID: String = "ACCOUNT_ID"
    }
}
