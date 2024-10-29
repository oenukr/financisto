package ru.orangesoftware.financisto.export.csv;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.export.ImportExportAsyncTask;

public class CsvExportTask extends ImportExportAsyncTask {

    private final CsvExportOptions options;

	public CsvExportTask(Activity context, ProgressDialog dialog, CsvExportOptions options) {
		super(context, dialog);
		this.options = options;
	}
	
	@Override
	protected Object work(@NonNull Context context, @NonNull DatabaseAdapter db, String...params) throws Exception {
		CsvExport export = new CsvExport(context, db, options);
        String backupFileName = export.export();
        if (options.getUploadToDropbox()) {
            doUploadToDropbox(context, backupFileName);
        }
        return backupFileName;
	}

	@Override
	protected String getSuccessMessage(Object result) {
		return String.valueOf(result);
	}

}
