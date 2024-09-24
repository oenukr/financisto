package ru.orangesoftware.financisto.activity

import android.content.Intent
import android.widget.CheckBox
import android.widget.Spinner
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.CurrencyExportPreferences

class CsvExportActivity : AbstractExportActivity(R.layout.csv_export) {

    private val currencyPreferences = CurrencyExportPreferences("csv")

    private val fieldSeparators by lazy { findViewById<Spinner>(R.id.spinnerFieldSeparator) }
    private val includeHeader by lazy { findViewById<CheckBox>(R.id.checkboxIncludeHeader) }
    private val exportSplits by lazy {
        CheckBox(this)
        //findViewById<CheckBox>(R.id.checkboxExportSplits)
    }
    private val uploadToDropbox by lazy { findViewById<CheckBox>(R.id.checkboxUploadToDropbox) }

    override fun internalOnCreate() { }

    override fun updateResultIntentFromUi(data: Intent?) {
        currencyPreferences.updateIntentFromUI(this, data)
        data?.putExtra(CSV_EXPORT_FIELD_SEPARATOR, fieldSeparators.selectedItem.toString()[1])
        data?.putExtra(CSV_EXPORT_INCLUDE_HEADER, includeHeader.isChecked)
        data?.putExtra(CSV_EXPORT_SPLITS, exportSplits.isChecked)
        data?.putExtra(CSV_EXPORT_UPLOAD_TO_DROPBOX, uploadToDropbox.isChecked)
    }

    override fun savePreferences() {
        val editor = getPreferences(MODE_PRIVATE).edit()
        currencyPreferences.savePreferences(this, editor)
        editor.putInt(CSV_EXPORT_FIELD_SEPARATOR, fieldSeparators.selectedItemPosition)
        editor.putBoolean(CSV_EXPORT_INCLUDE_HEADER, includeHeader.isChecked)
        editor.putBoolean(CSV_EXPORT_SPLITS, exportSplits.isChecked)
        editor.putBoolean(CSV_EXPORT_UPLOAD_TO_DROPBOX, uploadToDropbox.isChecked)
        editor.apply()
    }

    override fun restorePreferences() {
        val prefs = getPreferences(MODE_PRIVATE)
        currencyPreferences.restorePreferences(this, prefs)
        fieldSeparators.setSelection(prefs.getInt(CSV_EXPORT_FIELD_SEPARATOR, 0))
        includeHeader.setChecked(prefs.getBoolean(CSV_EXPORT_INCLUDE_HEADER, true))
        exportSplits.setChecked(prefs.getBoolean(CSV_EXPORT_SPLITS, false))
        uploadToDropbox.setChecked(prefs.getBoolean(CSV_EXPORT_UPLOAD_TO_DROPBOX, false))
    }

    companion object {
        const val CSV_EXPORT_FIELD_SEPARATOR = "CSV_EXPORT_FIELD_SEPARATOR"
        const val CSV_EXPORT_INCLUDE_HEADER = "CSV_EXPORT_INCLUDE_HEADER"
        const val CSV_EXPORT_SPLITS = "CSV_EXPORT_SPLITS"
        const val CSV_EXPORT_UPLOAD_TO_DROPBOX = "CSV_EXPORT_UPLOAD_TO_DROPBOX"
    }
}
