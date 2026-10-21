// SPDX-License-Identifier: GPL-2.0-only

package ru.orangesoftware.financisto.export.dropbox;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import java.io.IOException;

import ru.orangesoftware.financisto.backup.DatabaseExport;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.export.ImportExportAsyncTask;
import ru.orangesoftware.financisto.export.ImportExportException;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 11/9/11 2:23 AM
 */
public class DropboxBackupTask extends ImportExportAsyncTask {

    public DropboxBackupTask(Activity mainActivity, ProgressDialog dialog) {
        super(mainActivity, dialog);
    }

    @Override
    protected Object work(@NonNull Context context, @NonNull DatabaseAdapter db, String... params) throws ImportExportException, IOException, PackageManager.NameNotFoundException {
        DatabaseExport export = new DatabaseExport(context, db.db(), true);
        String backupFileName = export.export();
        doForceUploadToDropbox(context, backupFileName);
        return backupFileName;
    }

    @Override
    protected String getSuccessMessage(Object result) {
        return String.valueOf(result);
    }

}
