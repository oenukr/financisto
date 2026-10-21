# AsyncTask to Kotlin Coroutines Migration Roadmap

This document outlines the master implementation plan and step-by-step phased roadmap for migrating all legacy `AsyncTask` usages in Financisto to Kotlin Coroutines and converting affected components to idiomatic Kotlin.

It follows the guidelines defined in `.agents/skills/asynctask-to-coroutine/SKILL.md` and repository standards in `AGENTS.md`.

---

## Architecture Principles & Patterns

1. **Language & UI Scope**:
   * Fully convert touched files to idiomatic Kotlin. Do not leave hybrid Java `AsyncTask` workarounds.
   * Keep existing XML layouts and `ListAdapter`s across Phases 2–5 so each phase focuses strictly on Kotlin conversion and Coroutines/`ViewModel`/`StateFlow` migration, keeping PRs small and isolated.
2. **Structured Concurrency & Scopes**:
   * **LifecycleOwner (Activities / Fragments)**: Use `activity.lifecycleScope` directly. `AbstractActivity` and `AbstractListActivity` both extend `FragmentActivity` (implementing `LifecycleOwner` and `ViewModelStoreOwner`).
   * **ViewModels**: Use `viewModelScope` and register new `ViewModel`s, repositories, and `CoroutineDispatcher`s in Koin (`JavaAppKoin.kt`) using `viewModelOf` / `factoryOf` / `singleOf`.
   * **App-level / Non-lifecycle contexts**: Use `DependenciesHolder().applicationScope` (`CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` provided via Koin in `JavaAppKoin.kt`).
   * **Avoid**: Never use `GlobalScope` or unbounded thread creation.
3. **Threading**:
   * Perform database and network I/O explicitly on `withContext(Dispatchers.IO)`.
   * Perform heavy calculations on `withContext(Dispatchers.Default)`.
   * Ensure all UI updates and dialog interactions occur on `Dispatchers.Main`.
4. **State Management**:
   * For screen-level loaders, transition from imperative callback/listener patterns to `ViewModel` + `StateFlow<UiState>` + `repeatOnLifecycle`.
5. **Phase Isolation**:
   * Each phase represents an isolated, independently testable milestone suited for small, high-visibility pull requests.

---

## Master Priority Ranking (Easiest to Hardest)

| Phase | Tier | Target Files / Tasks | Complexity | Rationale | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 1** | Tier 1 | `SmsTemplateListAsyncAdapter` (`UpdateSortOrderTask`, `DeleteTask`)<br>`IntegrityCheckTask`<br>`MenuListItem` (`IntegrityFixTask`) | Very Low | Isolated operations, simple UI callbacks, self-contained background tasks | **COMPLETED** |
| **Phase 2** | Tier 2 | `AbstractListActivity`<br>`PurgeAccountActivity` (`PurgeAccountTask`)<br>`RateNode` (`RateDownloadTask`)<br>`ExchangeRatesListActivity` (`RatesDownloadTask`) | Low–Medium | Base `ListActivity` -> `FragmentActivity` migration + single-screen tasks with progress dialogs | **COMPLETED** |
| **Phase 3** | Tier 3 | `PlannerActivity` (`PlannerTask`)<br>`MonthlyViewActivity` (`MonthlyPreviewTask`) | Medium | Screen data-loading; benefits from ViewModel + StateFlow pattern | **COMPLETED** |
| **Phase 4** | Tier 4 | `AbstractTotalsDetailsActivity` (`CalculateAccountsTotalsTask`)<br>`BudgetListActivity` (`BudgetTotalsCalculationTask`)<br>`BudgetsTotalCalculator` | Medium–High | Complex calculations, aggregation queries, and list adapters | **PLANNED** |
| **Phase 5** | Tier 5 | `TotalCalculationTask` hierarchy (`AccountTotalCalculationTask`, `BlotterTotalCalculationTask`)<br>`ImportExportAsyncTask` hierarchy (9 concrete tasks) | High | Wide architectural impact across base classes and numerous callers/screens | **PLANNED** |

---

## Detailed Phase Breakdown

### Phase 1: Tier 1 – Infrastructure & Low-Risk Tasks (COMPLETED)

* **Goal**: Establish coroutine scope injection and migrate trivial fire-and-forget or callback-driven tasks.
* **Work Done**:
  1. [x] **Coroutine Scope Injection**: Added `coroutineModule` into `JavaAppKoin.kt` providing `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` and added `applicationScope` to `DependenciesHolder`.
  2. [x] **`SmsTemplateListAsyncAdapter.kt`**: Converted `SmsTemplateListAsyncAdapter.java` to Kotlin; replaced `UpdateSortOrderTask` and `DeleteTask` with `activity.lifecycleScope.launch` + `withContext(Dispatchers.IO)`.
  3. [x] **`IntegrityCheckTask.kt`**: Removed `AsyncTask` and `WeakReference`; refactored to coroutine execution using `lifecycleScope` / `applicationScope`.
  4. [x] **`MenuListItem.kt`**: Converted `MenuListItem.java` to Kotlin; replaced `IntegrityFixTask` with `doIntegrityFix(activity)` using coroutines.
  5. [x] **Verification**: 289 unit tests passed; debug assemble verified.

---

### Phase 2: Tier 2 – Single Activity Background Operations (COMPLETED)

* **Goal**: Modernize `AbstractListActivity` to `FragmentActivity` (`LifecycleOwner`) and migrate self-contained background tasks in single screens/widgets that display progress dialogs or UI indicators.
* **Work Done**:
  1. [x] **`AbstractListActivity.java` -> `AbstractListActivity.kt`**:
     * Converted base class to Kotlin and migrated from legacy `android.app.ListActivity` to `androidx.fragment.app.FragmentActivity` (preserving `getListView()`, `getListAdapter()`, `setListAdapter()`, and `onListItemClick()` helpers against `android.R.id.list` and `android.R.id.empty`) so all subclasses natively support `lifecycleScope` and `ViewModel`.
  2. [x] **`PurgeAccountActivity.java` -> `PurgeAccountActivity.kt`**:
     * Task: `PurgeAccountTask extends AsyncTask<Void, Void, Exception>`.
     * Action: Converted activity to Kotlin. Show progress dialog, execute `DatabaseExport` (if requested) and `db.purgeAccountAtDate(account, date)` on `withContext(Dispatchers.IO + NonCancellable)`, and handle UI completion (`setResult(RESULT_OK)`, `finish()`) and errors on Main.
  3. [x] **`RateNode.java` -> `RateNode.kt`**:
     * Task: `RateDownloadTask extends AsyncTask<Void, Void, ExchangeRate>`.
     * Action: Converted widget to Kotlin. Launch cancellable download `Job` tied to owner Activity `lifecycleScope`, call `ExchangeRateProvider.getRate` on `Dispatchers.IO` with `ensureActive()`, update rate input view on completion, and handle cancellation cleanly in `finally`.
  4. [x] **`ExchangeRatesListActivity.java` -> `ExchangeRatesListActivity.kt`**:
     * Task: `RatesDownloadTask extends AsyncTask<Void, Void, List<ExchangeRate>>`.
     * Action: Converted activity to Kotlin. Launch cancellable download `Job` with `lifecycleScope`, show progress dialog, download and persist latest exchange rates on `Dispatchers.IO`, and refresh list adapter on Main.
* **Verification**:
  * Static analysis: `./gradlew detekt`
  * Unit tests & build: `./gradlew :app:testDebugUnitTest assembleDebug`

---

### Phase 3: Tier 3 – Screen Data Loaders (ViewModel + StateFlow) (COMPLETED)

* **Goal**: Modernize screen data fetching into reactive MVVM/MVI with Koin-injected `ViewModel` and `StateFlow`, while keeping existing XML layouts and `ListAdapter`s.
* **Work Done**:
  1. [x] **`PlannerActivity.java` -> `PlannerActivity.kt` (+ `PlannerViewModel.kt`)**:
     * Task: `PlannerTask extends AsyncTask<Void, Void, TransactionList>` (lines 146–168).
     * Action: Converted `PlannerActivity` to Kotlin and deleted `PlannerActivity.java`. Extracted planning calculations (`FuturePlanner.getPlannedTransactionsWithTotals()`) into `PlannerViewModel` constructor-injecting `DatabaseAdapter` and `CoroutineDispatcher` (defaulting to `Dispatchers.IO`). Emits `StateFlow<PlannerUiState>` (`Loading`, `Success`, `Empty`, `Error`) collected via `repeatOnLifecycle(Lifecycle.State.STARTED)` in `PlannerActivity.kt`. Cancels prior in-flight jobs on filter reload. Registered in `JavaAppKoin.kt` via `viewModelOf(::PlannerViewModel)`.
  2. [x] **`MonthlyViewActivity.java` -> `MonthlyViewActivity.kt` (+ `MonthlyViewViewModel.kt`)**:
     * Task: `MonthlyPreviewTask extends AsyncTask<Void, Void, TransactionList>` (lines 365–424).
     * Action: Converted `MonthlyViewActivity` to Kotlin and deleted `MonthlyViewActivity.java`. Migrated base class from `android.app.ListActivity` to `androidx.fragment.app.FragmentActivity`, manually binding `android.R.id.list` and `android.R.id.empty`. Extracted monthly aggregation queries and statement calculations (`MonthlyViewPlanner`) into `MonthlyViewViewModel` constructor-injecting `DatabaseAdapter` and `CoroutineDispatcher` (defaulting to `Dispatchers.IO`). Emits `StateFlow<MonthlyViewUiState>` (`Loading`, `Success`, `Empty`, `Error`) collected via `repeatOnLifecycle(Lifecycle.State.STARTED)`. Registered in `JavaAppKoin.kt` via `viewModelOf(::MonthlyViewViewModel)`.
  3. [x] **Koin Dependency Injection**:
     * Registered `CoroutineDispatcher` (`Dispatchers.IO`) in `coroutineModule` and `viewModelModule` (`PlannerViewModel`, `MonthlyViewViewModel`) in `modules` list.
  4. [x] **Robolectric Unit Testing**:
     * Added comprehensive unit test suites in `PlannerViewModelTest.kt` and `MonthlyViewViewModelTest.kt` verifying state transitions, recurrence projection, credit card statement preview, error propagation, and job cancellation.
* **Verification**:
  * Static analysis: `./gradlew detekt` (0 findings)
  * Unit tests & build: `./gradlew :app:testDebugUnitTest assembleDebug`

---

### Phase 4: Tier 4 – Complex Totals Calculations (NEXT)

* **Goal**: Migrate multi-criteria total calculation tasks that update tab/summary counters using `lifecycleScope` and suspend functions.
* **Target Files & Conversions**:
  1. **`AbstractTotalsDetailsActivity.java` -> `AbstractTotalsDetailsActivity.kt`**:
     * Task: `CalculateAccountsTotalsTask extends AsyncTask<Void, Void, TotalsInfo>` (lines 80–141).
     * Action: Convert activity to Kotlin. Run `prepareInBackground()`, `getTotals()`, `getTotalInHomeCurrency()`, and exchange rate lookups on `Dispatchers.IO` via `lifecycleScope`. Update total header cards and account breakdown on Main.
  2. **`BudgetsTotalCalculator.java` -> `BudgetsTotalCalculator.kt` & `BudgetListActivity.java` -> `BudgetListActivity.kt`**:
     * Task: `BudgetTotalsCalculationTask extends AsyncTask<Void, Total, Total>` (lines 221–257).
     * Action: Convert `BudgetsTotalCalculator` and `BudgetListActivity` to Kotlin. Replace legacy `Handler` communication in `BudgetsTotalCalculator.updateBudgets` with suspend / dispatcher switches and launch total calculation in `BudgetListActivity` via `lifecycleScope`.
* **Verification**:
  * Unit tests: `./gradlew :app:testDebugUnitTest --tests "*Budget*" --tests "*Total*"`
  * Manual: Verify Account Totals screen and Budget balances update correctly across multiple currencies.

---

### Phase 5: Tier 5 – Core Base Class Hierarchies (Highest Complexity)

* **Goal**: Refactor project-wide base `AsyncTask` hierarchies and all derivative implementations across the app.
* **Target Hierarchies**:
  1. **`TotalCalculationTask.kt` Base Hierarchy**:
     * Implementations:
       * `AccountTotalCalculationTask`
       * `BlotterTotalCalculationTask`
       * `AccountListActivity.AccountTotalsCalculationTask`
       * `BudgetBlotterActivity` (anonymous `TotalCalculationTask`)
     * Consumed by: `AccountListActivity`, `BlotterActivity`, `BudgetBlotterActivity`, `SplitsBlotterActivity`, `BlotterTotalsDetailsActivity`.
     * Migration Strategy: Replace `AsyncTask` in `TotalCalculationTask` with coroutine execution (`Job` + `lifecycleScope` / `Dispatchers.IO`) or a domain calculator function, preserving clean cancellation (`stop()` / `cancel()`).
  2. **`ImportExportAsyncTask.java` Base Hierarchy**:
     * Base class: Handles background DB transaction setup, progress dialog management, and `EventBus` posting (`MenuListActivity` also migrated from `ListActivity` to `FragmentActivity`).
     * Concrete implementations (9 tasks):
       * `BackupExportTask`
       * `BackupImportTask`
       * `CsvExportTask`
       * `CsvImportTask`
       * `QifExportTask`
       * `QifImportTask`
       * `DropboxBackupTask`
       * `DropboxRestoreTask`
       * `DropboxListFilesTask`
     * Migration Strategy: Refactor `ImportExportAsyncTask` and its 9 concrete tasks to Kotlin Coroutines (`Dispatchers.IO` with main-thread progress updates and Koin-provided scopes/services). Update all invoking dialogs and activities.
* **Verification**:
  * Full regression test suite: `./gradlew :app:testDebugUnitTest`
  * Complete backup/restore, CSV import/export, and QIF import/export testing across local storage and cloud providers.
