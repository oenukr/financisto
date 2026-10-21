// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.PinProtection;

public abstract class AbstractSyncActivity extends Activity {

    public static final int IMPORT_FILENAME_REQUESTCODE = 0xff;

    private final int layoutId;


    public AbstractSyncActivity(int layoutId) {
        this.layoutId = layoutId;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutId);
        internalOnCreate();
    }

    protected abstract void internalOnCreate();

    protected abstract void updateResultIntentFromUi(Intent data);

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMPORT_FILENAME_REQUESTCODE) {

        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        PinProtection.lock(this);
        savePreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PinProtection.unlock(this);
        restorePreferences();
    }

    protected abstract void savePreferences();

    protected abstract void restorePreferences();

}
