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
import ru.orangesoftware.financisto.db.AppDatabase
import ru.orangesoftware.financisto.db.dao.*
import ru.orangesoftware.financisto.export.drive.GoogleDriveClient
import ru.orangesoftware.financisto.persistance.PreferencesStore
import ru.orangesoftware.financisto.repository.AccountRepository
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.TimberLogger
import ru.orangesoftware.financisto.utils.TimberTree
import ru.orangesoftware.financisto.viewmodel.AccountListViewModel
import timber.log.Timber
import org.koin.androidx.viewmodel.dsl.viewModel

// A module with Kotlin and Java components
val modules = module {
    single<PreferencesStore> { PreferencesStore(androidContext()) }
    single<GreenRobotBus> { GreenRobotBus() }
    single<GoogleDriveClient> { GoogleDriveClient(androidContext()) }
    single<AppDatabase> { AppDatabase.getInstance(androidContext()) }
    single<AccountDao> { get<AppDatabase>().accountDao() }
    single<BudgetDao> { get<AppDatabase>().budgetDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<CurrencyDao> { get<AppDatabase>().currencyDao() }
    single<ExchangeRateDao> { get<AppDatabase>().exchangeRateDao() }
    single<LocationDao> { get<AppDatabase>().locationDao() }
    single<PayeeDao> { get<AppDatabase>().payeeDao() }
    single<ProjectDao> { get<AppDatabase>().projectDao() }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<AttributeDao> { get<AppDatabase>().attributeDao() }
    single<SmsTemplateDao> { get<AppDatabase>().smsTemplateDao() }
    single<CategoryAttributeCrossRefDao> { get<AppDatabase>().categoryAttributeCrossRefDao() }
    single<TransactionAttributeValueDao> { get<AppDatabase>().transactionAttributeValueDao() }
    single<CreditCardClosingDateDao> { get<AppDatabase>().creditCardClosingDateDao() }

    // Add the Logger definition to the module
    single<Logger> { TimberLogger() }

    // Repositories
    single { AccountRepository(get(), get(), get()) }

    // ViewModels
    viewModel { AccountListViewModel(androidApplication(), get(), get(), get()) }
    viewModel { AccountViewModel(androidApplication(), get(), get(), get()) } // App, AccountDao, CurrencyDao, TransactionDao
    viewModel { TransactionViewModel(androidApplication(), get(), get(), get(), get(), get(), get()) } // App, TxDao, AccDao, CatDao, PayeeDao, CurrDao, TxAttrValDao
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
        Timber.plant(TimberTree())
    }
}

// Dependency holder
class DependenciesHolder : KoinComponent {
    val preferencesStore: PreferencesStore by inject()
    val greenRobotBus: GreenRobotBus by inject()
    val googleDriveClient: GoogleDriveClient by inject()
    //Inject the logger
    val logger: Logger by inject()
}
