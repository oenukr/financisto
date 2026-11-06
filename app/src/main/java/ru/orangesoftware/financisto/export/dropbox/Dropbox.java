// SPDX-License-Identifier: GPL-2.0-only

package ru.orangesoftware.financisto.export.dropbox;

import android.content.Context;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;

import com.dropbox.core.util.IOUtil;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.UploadUploader;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.export.ImportExportException;
import ru.orangesoftware.financisto.utils.Logger;
import ru.orangesoftware.financisto.utils.MyPreferences;

public class Dropbox {

    private final Logger logger = new DependenciesHolder().getLogger();

    private static final String APP_KEY = "INSERT_APP_KEY_HERE";

    private final Context context;

    private boolean startedAuth = false;
    private DbxClientV2 dropboxClient;
    private volatile UploadUploader activeUploader;
    private volatile InputStream activeUploadStream;

    public Dropbox(Context context) {
        this.context = context;
    }

    public void startAuth() {
        startedAuth = true;
        Auth.startOAuth2Authentication(context, APP_KEY);
    }

    public void completeAuth() {
        try {
            String authToken = Auth.getOAuth2Token();
            if (startedAuth && authToken != null) {
                try {
                    MyPreferences.storeDropboxKeys(context, authToken);
                } catch (IllegalStateException e) {
                    logger.i(e, "Error authenticating Dropbox");
                }
            }
        } finally {
            startedAuth = false;
        }
    }

    public void deAuth() {
        MyPreferences.removeDropboxKeys(context);
        if (dropboxClient != null) {
            try {
                dropboxClient.auth().tokenRevoke();
            } catch (DbxException e) {
                logger.e(e, "Unable to unlink Dropbox");
            }
        }
    }

    private boolean authSession() {
        String accessToken = MyPreferences.getDropboxAuthToken(context);
        if (accessToken != null) {
            if (dropboxClient == null) {
                DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder("financisto")
                        // TODO: Implement a Ktor-based DbxHttpRequestor to align with the app's networking stack.
                        // .withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
                        .build();
                dropboxClient = new DbxClientV2(requestConfig, accessToken);
            }
            return true;
        }
        return false;
    }

    public void cancelUpload() {
        UploadUploader uploader = activeUploader;
        if (uploader != null) {
            uploader.abort();
            uploader.close();
        }
        InputStream stream = activeUploadStream;
        if (stream != null) {
            IOUtil.closeInput(stream);
        }
    }

    public FileMetadata uploadFile(File file) throws ImportExportException, FileNotFoundException {
        return uploadFile(file, () -> false);
    }

    public FileMetadata uploadFile(File file, BooleanSupplier isCancelled) throws ImportExportException, FileNotFoundException {
        if (authSession()) {
            if (isCancelled.getAsBoolean()) {
                throw new ImportExportException(R.string.dropbox_error, new IOException("Dropbox upload cancelled"));
            }
            try {
                InputStream is = new FileInputStream(file);
                activeUploadStream = is;
                UploadUploader uploader = null;
                try {
                    if (isCancelled.getAsBoolean()) {
                        throw new IOException("Dropbox upload cancelled");
                    }
                    uploader = dropboxClient.files().uploadBuilder("/" + file.getName()).withMode(WriteMode.ADD).start();
                    activeUploader = uploader;
                    final UploadUploader currentUploader = uploader;
                    InputStream cancellableStream = new FilterInputStream(is) {
                        @Override
                        public int read() throws IOException {
                            checkNotCancelled();
                            return super.read();
                        }

                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            checkNotCancelled();
                            return super.read(b, off, len);
                        }

                        private void checkNotCancelled() throws IOException {
                            if (isCancelled.getAsBoolean()) {
                                currentUploader.abort();
                                throw new IOException("Dropbox upload cancelled");
                            }
                        }
                    };
                    FileMetadata fileMetadata = uploader.uploadAndFinish(cancellableStream, bytesWritten -> {
                        if (isCancelled.getAsBoolean()) {
                            currentUploader.abort();
                        }
                    });
                    if (isCancelled.getAsBoolean()) {
                        uploader.abort();
                        throw new IOException("Dropbox upload cancelled");
                    }
                    logger.i("Dropbox: The uploaded file's rev is: %s", fileMetadata.getRev());
                    return fileMetadata;
                } finally {
                    activeUploader = null;
                    activeUploadStream = null;
                    if (uploader != null) {
                        uploader.close();
                    }
                    IOUtil.closeInput(is);
                }
            } catch (IllegalArgumentException | DbxException | IOException e) {
                logger.e(e, "Dropbox: Something wrong");
                throw new ImportExportException(R.string.dropbox_error, e);
            }
        } else {
            throw new ImportExportException(R.string.dropbox_auth_error);
        }
    }

    List<String> listFiles() throws Exception {
        if (authSession()) {
            try {
                List<String> files = new ArrayList<>();
                ListFolderResult listFolderResult = dropboxClient.files().listFolder("");
                for (Metadata metadata : listFolderResult.getEntries()) {
                    String name = metadata.getName();
                    if (name.endsWith(".backup")) {
                        files.add(name);
                    }
                }
                files.sort(Comparator.reverseOrder());
                return files;
            } catch (Exception e) {
                logger.e(e, "Dropbox: Something wrong");
                throw new ImportExportException(R.string.dropbox_error, e);
            }
        } else {
            throw new ImportExportException(R.string.dropbox_auth_error);
        }
    }

    public InputStream getFileAsStream(String backupFile) throws Exception {
        if (authSession()) {
            try {
                return dropboxClient.files().downloadBuilder("/" + backupFile).start().getInputStream();
            } catch (Exception e) {
                logger.e(e, "Dropbox: Something wrong");
                throw new ImportExportException(R.string.dropbox_error, e);
            }
        } else {
            throw new ImportExportException(R.string.dropbox_auth_error);
        }
    }
}
