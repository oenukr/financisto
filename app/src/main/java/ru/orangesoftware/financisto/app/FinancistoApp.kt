package ru.orangesoftware.financisto.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import org.koin.core.context.stopKoin

import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.export.drive.GoogleDriveClient
import ru.orangesoftware.financisto.utils.MyPreferences

class FinancistoApp : Application() {

    lateinit var bus: GreenRobotBus
    lateinit var driveClient: GoogleDriveClient

    override fun onCreate() {
        init()
        super.onCreate()
    }

    fun init() {
        val dependencies = DependenciesHolder()
        bus = dependencies.greenRobotBus
        driveClient = dependencies.googleDriveClient
        bus.register(driveClient)
    }

    override fun attachBaseContext(base: Context?) {
        start(this)
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        MyPreferences.switchLocale(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        stopKoin()
    }
}
