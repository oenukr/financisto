package ru.orangesoftware.financisto.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.dsl.module
import ru.orangesoftware.financisto.BuildConfig
import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.export.drive.GoogleDriveClient
import ru.orangesoftware.financisto.persistance.PreferencesStore
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.TimberLogger
import timber.log.Timber

// A module with Kotlin and Java components
val modules = module {
    single<PreferencesStore> { PreferencesStore(androidContext()) }
    single<GreenRobotBus> { GreenRobotBus() }
    single<GoogleDriveClient> { GoogleDriveClient(androidContext()) }
    single<DatabaseAdapter> { DatabaseAdapter(androidContext()) }
    single<DatabaseHelper> { DatabaseHelper(androidContext()) }

    // Add the Logger definition to the module
    single<Logger> { TimberLogger() }
}

// Start
fun start(myApplication: Application) {
    // Start Koin with given Application instance
    startKoin {
        // Log Koin into Android logger
        androidLogger()
        // Reference Android context
        androidContext(myApplication)
        // Load modules
        modules(listOf(modules))
    }

    // Plant Timber tree only once when start Koin.
    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    }
}

// Dependency holder
class DependenciesHolder : KoinComponent {
    val preferencesStore: PreferencesStore by inject()
    val greenRobotBus: GreenRobotBus by inject()
    val googleDriveClient: GoogleDriveClient by inject()
    val databaseAdapter: DatabaseAdapter by inject()
    val databaseHelper: DatabaseHelper by inject()
    //Inject the logger
    val logger: Logger by inject()
}
