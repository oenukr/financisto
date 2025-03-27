/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.export;

import static ru.orangesoftware.financisto.export.Export.uploadBackupFileToDropbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.bus.RefreshCurrentTab;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.utils.Logger;
import ru.orangesoftware.financisto.utils.MyPreferences;
//import static ru.orangesoftware.financisto.export.Export.uploadBackupFileToGoogleDrive;

public abstract class ImportExportAsyncTask extends AsyncTask<String, String, Object> {

    private final Logger logger = new DependenciesHolder().getLogger();

    protected final Activity context;
    protected final ProgressDialog dialog;
    private boolean showResultMessage = true;

    private ImportExportAsyncTaskListener listener;

    public ImportExportAsyncTask(Activity context, ProgressDialog dialog) {
        this.dialog = dialog;
        this.context = context;
    }

    public void setListener(ImportExportAsyncTaskListener listener) {
        this.listener = listener;
    }

    public void setShowResultMessage(boolean showResultMessage) {
        this.showResultMessage = showResultMessage;
    }

    @Override
    protected Object doInBackground(String... params) {
        DatabaseAdapter db = new DatabaseAdapter(context);
        db.open();
        try {
            return work(context, db, params);
        } catch (Exception ex) {
            logger.e(ex, "Unable to do import/export");
            return ex;
        } finally {
            db.close();
        }
    }

    protected abstract Object work(@NonNull Context context, @NonNull DatabaseAdapter db, String... params) throws Exception;

    protected abstract String getSuccessMessage(Object result);

    protected void doUploadToDropbox(Context context, String backupFileName) throws FileNotFoundException, ImportExportException {
        if (MyPreferences.isDropboxUploadBackups(context)) {
            doForceUploadToDropbox(context, backupFileName);
        }
    }

    protected void doForceUploadToDropbox(Context context, String backupFileName) throws FileNotFoundException, ImportExportException {
        publishProgress(context.getString(R.string.dropbox_uploading_file));
        uploadBackupFileToDropbox(context, backupFileName);
    }

//    void doUploadToGoogleDrive(Context context, String backupFileName) throws Exception {
//        if (MyPreferences.isGoogleDriveUploadBackups(context)) {
//            doForceUploadToGoogleDrive(context, backupFileName);
//        }
//    }

//    private void doForceUploadToGoogleDrive(Context context, String backupFileName) throws Exception {
//        publishProgress(context.getString(R.string.google_drive_uploading_file));
//        uploadBackupFileToGoogleDrive(context, backupFileName);
//    }


    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        dialog.setMessage(values[0]);
    }

    @Override
    protected void onPostExecute(Object result) {
        dialog.dismiss();

        if (result instanceof ImportExportException exception) {
            StringBuilder sb = new StringBuilder();
            if (exception.getFormatArgs() != null){
                sb.append(context.getString(exception.getErrorResId(), exception.getFormatArgs()));
            } else {
                sb.append(context.getString(exception.getErrorResId()));
            }

            if (exception.getCause() != null) {
                sb.append(" : ").append(exception.getCause());
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.fail)
                    .setMessage(sb.toString())
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        if (result instanceof Exception)
            return;

        String message = getSuccessMessage(result);

        refreshMainActivity();
        if (listener != null) {
            listener.onCompleted(result);
        }

        if (showResultMessage) {
            Toast.makeText(context, context.getString(R.string.success, message), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshMainActivity() {
        new DependenciesHolder().getGreenRobotBus().post(new RefreshCurrentTab());
    }

}
