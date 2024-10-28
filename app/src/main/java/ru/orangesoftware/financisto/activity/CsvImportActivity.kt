package ru.orangesoftware.financisto.activity

import android.content.Intent
import android.content.SharedPreferences
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.MyEntityAdapter
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.utils.CurrencyExportPreferences

class CsvImportActivity : AbstractImportActivity(R.layout.csv_import) {

    companion object {
        const val CSV_IMPORT_SELECTED_ACCOUNT_2 = "CSV_IMPORT_SELECTED_ACCOUNT_2"
        const val CSV_IMPORT_DATE_FORMAT = "CSV_IMPORT_DATE_FORMAT"
        const val CSV_IMPORT_FILENAME = "CSV_IMPORT_FILENAME"
        const val CSV_IMPORT_FIELD_SEPARATOR = "CSV_IMPORT_FIELD_SEPARATOR"
        const val CSV_IMPORT_USE_HEADER_FROM_FILE = "CSV_IMPORT_USE_HEADER_FROM_FILE"
    }

    private val currencyPreferences: CurrencyExportPreferences = CurrencyExportPreferences("csv")

    private lateinit var db: DatabaseAdapter
    private lateinit var accounts: List<Account>
    private lateinit var accountSpinner: Spinner
    private lateinit var useHeaderFromFile: CheckBox

    override fun internalOnCreate() {
        db = DatabaseAdapter(this)
        db.open()

        accounts = db.getAllAccountsList()
        val accountsAdapter: ArrayAdapter<Account> = MyEntityAdapter(
            this,
            android.R.layout.simple_spinner_item,
            android.R.id.text1,
            accounts,
        )
        accountsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accountSpinner = findViewById(R.id.spinnerAccount)
        accountSpinner.setAdapter(accountsAdapter)

        useHeaderFromFile = findViewById(R.id.cbUseHeaderFromFile)

        val bOk: Button = findViewById(R.id.bOK)
        bOk.setOnClickListener {
            if (edFilename.getText().toString().isEmpty()) {
                Toast.makeText(this@CsvImportActivity, R.string.select_filename, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val data = Intent()
            updateResultIntentFromUi(data)
            setResult(RESULT_OK, data)
            finish()
        }

        val bCancel: Button = findViewById(R.id.bCancel)
        bCancel.setOnClickListener { _ ->
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    override fun updateResultIntentFromUi(data: Intent?) {
        currencyPreferences.updateIntentFromUI(this, data)
        data?.putExtra(CSV_IMPORT_SELECTED_ACCOUNT_2, getSelectedAccountId())
        val dateFormats: Spinner = findViewById(R.id.spinnerDateFormats)
        data?.putExtra(CSV_IMPORT_DATE_FORMAT, dateFormats.getSelectedItem().toString())
        data?.putExtra(CSV_IMPORT_FILENAME, edFilename.getText().toString())
        val fieldSeparator: Spinner = findViewById(R.id.spinnerFieldSeparator)
        data?.putExtra(CSV_IMPORT_FIELD_SEPARATOR, fieldSeparator.getSelectedItem().toString()[1])
        data?.putExtra(CSV_IMPORT_USE_HEADER_FROM_FILE, useHeaderFromFile.isChecked)
    }

    override fun savePreferences() {
        val editor: SharedPreferences.Editor = getPreferences(MODE_PRIVATE).edit()

        currencyPreferences.savePreferences(this, editor)
        editor.putLong(CSV_IMPORT_SELECTED_ACCOUNT_2, getSelectedAccountId())
        val dateFormats: Spinner = findViewById(R.id.spinnerDateFormats)
        editor.putInt(CSV_IMPORT_DATE_FORMAT, dateFormats.selectedItemPosition)
        editor.putString(CSV_IMPORT_FILENAME, edFilename.getText().toString())
        val fieldSeparator: Spinner = findViewById(R.id.spinnerFieldSeparator)
        editor.putInt(CSV_IMPORT_FIELD_SEPARATOR, fieldSeparator.selectedItemPosition)
        editor.putBoolean(CSV_IMPORT_USE_HEADER_FROM_FILE, useHeaderFromFile.isChecked)
        editor.apply()
    }

    override fun restorePreferences() {
        val preferences: SharedPreferences = getPreferences(MODE_PRIVATE)

        currencyPreferences.restorePreferences(this, preferences)

        val selectedAccountId: Long = preferences.getLong(CSV_IMPORT_SELECTED_ACCOUNT_2, 0)
        selectedAccount(selectedAccountId)

        val dateFormats: Spinner = findViewById(R.id.spinnerDateFormats)
        dateFormats.setSelection(preferences.getInt(CSV_IMPORT_DATE_FORMAT, 0))
        edFilename = findViewById(R.id.edFilename)
        edFilename.setText(preferences.getString(CSV_IMPORT_FILENAME, ""))
        val fieldSeparator: Spinner = findViewById(R.id.spinnerFieldSeparator)
        fieldSeparator.setSelection(preferences.getInt(CSV_IMPORT_FIELD_SEPARATOR, 0))
        useHeaderFromFile.setChecked(preferences.getBoolean(CSV_IMPORT_USE_HEADER_FROM_FILE, true))
    }

    private fun getSelectedAccountId(): Long = accountSpinner.selectedItemId

    private fun selectedAccount(selectedAccountId: Long) {
        accounts.forEachIndexed { index, account ->
            if (account.id == selectedAccountId) {
                accountSpinner.setSelection(index)
                return
            }
        }
    }
}
