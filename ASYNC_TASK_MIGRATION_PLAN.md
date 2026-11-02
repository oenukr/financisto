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
| **Phase 4** | Tier 4 | `AbstractTotalsDetailsActivity` (`CalculateAccountsTotalsTask`)<br>`BudgetListActivity` (`BudgetTotalsCalculationTask`)<br>`BudgetsTotalCalculator` | Medium–High | Complex calculations, aggregation queries, and list adapters | **COMPLETED** |
| **Phase 5** | Tier 5 | `TotalCalculationTask` hierarchy (`AccountTotalCalculationTask`, `BlotterTotalCalculationTask`)<br>`ImportExportAsyncTask` hierarchy (9 concrete tasks) | High | Wide architectural impact across base classes and numerous callers/screens | **COMPLETED** |

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

### Phase 4: Tier 4 – Complex Totals Calculations (COMPLETED)

* **Goal**: Migrate multi-criteria total calculation tasks that update tab/summary counters using `lifecycleScope` and suspend functions.
* **Work Done**:
  1. [x] **`AbstractTotalsDetailsActivity.java` -> `AbstractTotalsDetailsActivity.kt`**:
     * Task: `CalculateAccountsTotalsTask extends AsyncTask<Void, Void, TotalsInfo>` (lines 80–141).
     * Action: Converted activity to Kotlin and deleted `AbstractTotalsDetailsActivity.java`. Replaced `CalculateAccountsTotalsTask` with a cancellable coroutine `Job` on `lifecycleScope`, running `prepareInBackground()`, `getTotals()`, `getTotalInHomeCurrency()`, and exchange rate lookups on `Dispatchers.IO` with `ensureActive()`, and updating total header cards and account breakdown on Main.
  2. [x] **`BudgetsTotalCalculator.java` -> `BudgetsTotalCalculator.kt` & `BudgetListActivity.java` -> `BudgetListActivity.kt`**:
     * Task: `BudgetTotalsCalculationTask extends AsyncTask<Void, Total, Total>` (lines 221–257).
     * Action: Converted `BudgetsTotalCalculator` and `BudgetListActivity` to Kotlin. Replaced legacy `Handler` communication in `BudgetsTotalCalculator.updateBudgets` with `suspend fun updateBudgets(mainDispatcher: CoroutineDispatcher? = null)` and launched total calculation in `BudgetListActivity` via `lifecycleScope` over an immutable snapshot of budgets. Updated `BudgetListTotalsDetailsActivity.prepareInBackground()` to `suspend`.
  3. [x] **Robolectric Unit Testing**:
     * Added `BudgetsTotalCalculatorTest.kt` covering budget balance updates, main-dispatcher switching, multi-category/project formatting, cooperative cancellation, currency total aggregation, and home-currency conversion.
* **Verification**:
  * Static analysis: `./gradlew detekt`
  * Unit tests & build: `./gradlew :app:testDebugUnitTest assembleDebug`

---

### Phase 5: Tier 5 – Core Base Class Hierarchies (Highest Complexity) (COMPLETED)

* **Goal**: Refactor project-wide base `AsyncTask` hierarchies and all derivative implementations across the app, achieving zero `android.os.AsyncTask` references across the entire codebase.
* **Work Done**:
  1. [x] **Test-First Baseline Coverage**:
     * Implemented comprehensive unit tests prior to refactoring: `TotalCalculationTaskTest.kt`, `TotalCalculationTaskAdversarialTest.kt`, `ImportExportTasksTest.kt`, `ImportExportTasksAdversarialTest.kt`, `AccountListActivityTest.kt`, `BlotterActivityTest.kt`, and `MenuListActivityTest.kt`.
  2. [x] **`TotalCalculationTask.kt` Base Hierarchy**:
     * Refactored base class `TotalCalculationTask.kt` removing all `android.os.AsyncTask` inheritance and replacing it with Kotlin Coroutines (`Job`, injected `CoroutineDispatcher = Dispatchers.IO`, and cooperative cancellation via `coroutineContext.ensureActive()`). Retained synchronous calculation methods (`getTotalInHomeCurrency()` and `getTotals()`) for `BlotterTotalsDetailsActivity`.
     * Modernized derived tasks: `AccountTotalCalculationTask.kt` and `BlotterTotalCalculationTask.kt`.
     * Converted host Java activities to Kotlin: `AccountListActivity.java` -> `AccountListActivity.kt` and `BlotterActivity.java` -> `BlotterActivity.kt`, launching calculations on `lifecycleScope` and ensuring explicit job cancellation in `onDestroy()`.
     * Updated calling screens: `BudgetBlotterActivity.kt`, `SplitsBlotterActivity.kt`, and `BlotterTotalsDetailsActivity.kt`.
  3. [x] **`ImportExportAsyncTask.java` -> `ImportExportAsyncTask.kt` Base Hierarchy**:
     * Converted `ImportExportAsyncTask.java` to Kotlin coroutines (`ImportExportAsyncTask.kt`), launching work on injected `ioDispatcher` via configurable scope (`DependenciesHolder().applicationScope` by default), managing database lifecycle (`db.close()` in `finally`), safely dispatching progress and completion to `mainDispatcher` with lifecycle checks (`!context.isFinishing && !context.isDestroyed`), and supporting synchronous `get()` via `CompletableDeferred`.
     * Updated `ImportExportAsyncTaskListener.kt` to accept nullable results (`fun onCompleted(result: Any?)`).
     * Converted 6 Java tasks to Kotlin and deleted their `.java` files: `CsvExportTask.kt`, `QifExportTask.kt`, `QifImportTask.kt`, `DropboxBackupTask.kt`, `DropboxRestoreTask.kt`, and `DropboxListFilesTask.kt`.
     * Modernized 3 existing Kotlin tasks to `override suspend fun work(...)`: `BackupExportTask.kt`, `BackupImportTask.kt`, and `CsvImportTask.kt`.
  4. [x] **`MenuListActivity.java` -> `MenuListActivity.kt`**:
     * Converted `MenuListActivity` to Kotlin and migrated base class from legacy `android.app.ListActivity` to `androidx.fragment.app.FragmentActivity`, maintaining manual `ListView` binding and 100% binary/source compatibility with generated `MenuListActivity_.java`.
     * Preserved static inner classes (`StartDropboxBackup`, `StartDropboxRestore`, `StartDriveBackup`, `StartDriveRestore`) for `EventBus` compatibility.
  5. [x] **Complete `android.os.AsyncTask` Eradication**:
     * Verified zero occurrences of `android.os.AsyncTask` across the entire codebase (`app/src/`).
* **Verification**:
  * Zero legacy occurrences: `git grep -n "android.os.AsyncTask" app/src/` returned exit code 1 (0 matches).
  * Full regression test suite & build: `./gradlew cleanTestDebugUnitTest :app:testDebugUnitTest assembleDebug` (389 tests passed, 0 failures, 0 skipped, debug APK generated).
  * Static analysis: `./gradlew detekt` (0 issues / 0 warnings).

---

## Migration Completion Summary

All 5 tiers of the `AsyncTask` to Kotlin Coroutines migration have been **100% COMPLETED**:
- **Phase 1 (Tier 1)**: Infrastructure, scope injection, and low-risk tasks (`SmsTemplateListAsyncAdapter`, `IntegrityCheckTask`, `MenuListItem`).
- **Phase 2 (Tier 2)**: `AbstractListActivity` to `FragmentActivity`, single-activity tasks (`PurgeAccountActivity`, `RateNode`, `ExchangeRatesListActivity`).
- **Phase 3 (Tier 3)**: Screen data loaders migrated to MVVM/MVI with Koin-injected `ViewModel` and `StateFlow` (`PlannerActivity`, `MonthlyViewActivity`).
- **Phase 4 (Tier 4)**: Complex multi-criteria totals calculations (`AbstractTotalsDetailsActivity`, `BudgetListActivity`, `BudgetsTotalCalculator`).
- **Phase 5 (Tier 5)**: Core base class hierarchies (`TotalCalculationTask` and `ImportExportAsyncTask` hierarchies, 9 concrete tasks, `AccountListActivity`, `BlotterActivity`, `MenuListActivity`).

**Outcome**: Exactly **0 occurrences** of `android.os.AsyncTask` remain across Financisto. All asynchronous operations now utilize Kotlin Coroutines with structured concurrency, lifecycle-aware scopes, and injected dispatchers.

