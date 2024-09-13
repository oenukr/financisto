package ru.orangesoftware.financisto.app;

import static org.koin.core.context.DefaultContextExtKt.stopKoin;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import ru.orangesoftware.financisto.bus.GreenRobotBus;
import ru.orangesoftware.financisto.export.drive.GoogleDriveClient;
import ru.orangesoftware.financisto.utils.MyPreferences;

public class FinancistoApp extends Application {

    public GreenRobotBus bus;
    public GoogleDriveClient driveClient;

    @Override
    public void onCreate() {
        init();
        super.onCreate();
    }

    public void init() {
        JavaAppKoinKt.start(this);
        DependenciesHolder dependencies = new DependenciesHolder();
        bus = dependencies.getGreenRobotBus();
        driveClient = dependencies.getGoogleDriveClient();
        bus.register(driveClient);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(MyPreferences.switchLocale(base));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        MyPreferences.switchLocale(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        stopKoin();
    }
}
