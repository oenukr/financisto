// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appstate.statestore.StateStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import ru.orangesoftware.financisto.BuildConfig
import ru.orangesoftware.financisto.activity.MonthlyViewViewModel
import ru.orangesoftware.financisto.activity.PlannerViewModel
import ru.orangesoftware.financisto.appfunctions.AppFunctionDatabase
import ru.orangesoftware.financisto.appstate.AiDraftSessionKey
import ru.orangesoftware.financisto.appstate.AppStateSyncController
import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.export.drive.GoogleDriveClient
import ru.orangesoftware.financisto.http.HttpClientWrapper
import ru.orangesoftware.financisto.persistance.PreferencesStore
import ru.orangesoftware.financisto.rates.FreeCurrencyRateDownloader
import ru.orangesoftware.financisto.rates.OpenExchangeRatesDownloader
import ru.orangesoftware.financisto.rates.WebserviceXConversionRateDownloader
import ru.orangesoftware.financisto.reports.reportsAppStateModule
import ru.orangesoftware.financisto.transaction.FinancistoDraftSessionStore
import ru.orangesoftware.financisto.transaction.FinancistoExchangeRatePort
import ru.orangesoftware.financisto.transaction.FinancistoReceiptMediaPort
import ru.orangesoftware.financisto.transaction.FinancistoTransactionPreferencesPort
import ru.orangesoftware.financisto.transaction.FinancistoTransactionRepository
import ru.orangesoftware.financisto.transaction.di.transactionModule
import ru.orangesoftware.financisto.transaction.port.DraftSessionStore
import ru.orangesoftware.financisto.transaction.port.ExchangeRatePort
import ru.orangesoftware.financisto.transaction.port.ReceiptMediaPort
import ru.orangesoftware.financisto.transaction.port.TransactionPreferencesPort
import ru.orangesoftware.financisto.transaction.port.TransactionRepository
import ru.orangesoftware.financisto.ui.uiAppStateModule
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.TimberLogger
import ru.orangesoftware.financisto.utils.TimberTree
import timber.log.Timber
import kotlin.time.Clock as KotlinXClock

@Suppress("TopLevelPropertyNaming")
const val TRANSFORM_DISPATCHER_NAME = "transformDispatcher"

@Suppress("TopLevelPropertyNaming")
val TRANSFORM_DISPATCHER = named(TRANSFORM_DISPATCHER_NAME)

// A module with Kotlin and Java components
val storage = module {
    single<SharedPreferences> {
        androidContext().getSharedPreferences("${androidContext().packageName}_preferences", Context.MODE_PRIVATE)
    }
    single { PreferencesStore(androidContext(), get(), get()) }
}

val eventBus = module {
    singleOf(::GreenRobotBus) { bind<GreenRobotBus>() }
}

val remoteStorage = module {
    singleOf(::GoogleDriveClient) { bind<GoogleDriveClient>() }
}

val database = module {
    singleOf(::DatabaseAdapter) { bind<DatabaseAdapter>() }
    singleOf(::DatabaseHelper) { bind<DatabaseHelper>() }
    singleOf(::AppFunctionDatabaseImpl) { bind<AppFunctionDatabase>() }
}

val logger = module {
    // Add the Logger definition to the module
    singleOf(::TimberLogger) { bind<Logger>() }
}

val timeModule = module {
    single<KotlinXClock> { KotlinXClock.System }
}

val httpClient = module {
    single<HttpClientEngine> { CIO.create() } onClose { it?.close() }
    single<HttpClient> {
        HttpClient(get()) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    } onClose { it?.close() }
    singleOf(::HttpClientWrapper) { bind<HttpClientWrapper>() }
}

val exchangeRates = module {
    factory<FreeCurrencyRateDownloader> {
        FreeCurrencyRateDownloader(get(), get(), get<KotlinXClock>())
    }
    factory<WebserviceXConversionRateDownloader> {
        WebserviceXConversionRateDownloader(get(), get(), get<KotlinXClock>())
    }
    factory<OpenExchangeRatesDownloader> {
        OpenExchangeRatesDownloader(get(), get(), get(), get<KotlinXClock>())
    }
}

@Suppress("InjectDispatcher")
val coroutineModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) } onClose { it?.cancel() }
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<CoroutineDispatcher>(TRANSFORM_DISPATCHER) { Dispatchers.Default }
}

val appStateModule = module {
    single { StateStore() }
    single { AppStateSyncController(androidContext(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModelOf(::PlannerViewModel)
    viewModelOf(::MonthlyViewViewModel)
}

val transactionPortsModule = module {
    singleOf(::FinancistoTransactionRepository) { bind<TransactionRepository>() }
    singleOf(::FinancistoTransactionPreferencesPort) { bind<TransactionPreferencesPort>() }
    single<FinancistoDraftSessionStore> {
        val syncController: AppStateSyncController = get()
        FinancistoDraftSessionStore(
            context = androidContext(),
            stateStore = get(),
            ioDispatcher = get(),
            persistSessionState = { state -> syncController.persistKey(AiDraftSessionKey, state) },
        )
    } bind DraftSessionStore::class
    singleOf(::FinancistoExchangeRatePort) { bind<ExchangeRatePort>() }
    singleOf(::FinancistoReceiptMediaPort) { bind<ReceiptMediaPort>() }
}

val modules = listOf(
    storage,
    eventBus,
    remoteStorage,
    database,
    logger,
    timeModule,
    httpClient,
    exchangeRates,
    coroutineModule,
    appStateModule,
    reportsAppStateModule,
    uiAppStateModule,
    viewModelModule,
    transactionPortsModule,
    transactionModule,
)

// Start
fun start(myApplication: Application) {
    // Start Koin with given Application instance
    startKoin {
        // Log Koin into Android logger
        androidLogger()
        // Reference Android context
        androidContext(myApplication)
        // Load modules
        modules(modules)
    }

    // Plant Timber tree only once when start Koin.
    if (BuildConfig.DEBUG) {
        Timber.plant(TimberTree())
    }
}

// Dependency holder
class DependenciesHolder : KoinComponent {
    val preferencesStore: PreferencesStore by inject()
    val greenRobotBus: GreenRobotBus by inject()
    val googleDriveClient: GoogleDriveClient by inject()
    val databaseAdapter: DatabaseAdapter by inject()
    val databaseHelper: DatabaseHelper by inject()
    val httpClientWrapper: HttpClientWrapper by inject()
    //Inject the logger
    val logger: Logger by inject()
    val applicationScope: CoroutineScope by inject()
    val appStateSyncController: AppStateSyncController by inject()
    val stateStore: StateStore by inject()
}
