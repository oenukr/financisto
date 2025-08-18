package ru.orangesoftware.financisto.export.csv;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

import ru.orangesoftware.financisto.db.CurrencyDao;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.db.FinancistoDatabase;
import ru.orangesoftware.financisto.export.ImportExportAsyncTask;

public class CsvExportTask extends ImportExportAsyncTask {

    private final CsvExportOptions options;
    private final CurrencyDao currencyDao;

	public CsvExportTask(Activity context, ProgressDialog dialog, CsvExportOptions options) {
		super(context, dialog);
		this.options = options;
        FinancistoDatabase roomDb = Room.databaseBuilder(context.getApplicationContext(),
                FinancistoDatabase.class, "financisto.db").build();
        this.currencyDao = roomDb.currencyDao();
	}
	
	@Override
	protected Object work(@NonNull Context context, @NonNull DatabaseAdapter db, String...params) throws Exception {
		CsvExport export = new CsvExport(context, db, options, currencyDao);
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
