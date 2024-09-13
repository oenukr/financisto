//
// DO NOT EDIT THIS FILE.
// Generated using AndroidAnnotations 4.8.0.
// 
// You can create a larger work that contains this file and distribute that work under terms of your choice.
//

package ru.orangesoftware.financisto.export.drive;

import android.content.Context;

import org.androidannotations.api.view.OnViewChangedNotifier;

public final class GoogleDriveClient_
    extends GoogleDriveClient
{
    private Context context_;
    private Object rootFragment_;
    private static GoogleDriveClient_ instance_;

    private GoogleDriveClient_(Context context) {
        super(context);
        context_ = context;
    }

    private GoogleDriveClient_(Context context, Object rootFragment) {
        super(context);
        context_ = context;
        rootFragment_ = rootFragment;
    }

    public static GoogleDriveClient_ getInstance_(Context context) {
        if (instance_ == null) {
            OnViewChangedNotifier previousNotifier = OnViewChangedNotifier.replaceNotifier(null);
            instance_ = new GoogleDriveClient_(context.getApplicationContext());
            instance_.init_();
            OnViewChangedNotifier.replaceNotifier(previousNotifier);
        }
        return instance_;
    }

    private void init_() {
        init();
    }
}
