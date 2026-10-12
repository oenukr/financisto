# AsyncTask to Kotlin Coroutines Migration Roadmap

This document outlines the master implementation plan and step-by-step phased roadmap for migrating all legacy `AsyncTask` usages in Financisto to Kotlin Coroutines and converting affected components to idiomatic Kotlin.

It follows the guidelines defined in `.agents/skills/asynctask-to-coroutine/SKILL.md` and repository standards in `AGENTS.md`.

---

## Architecture Principles & Patterns

1. **Language**: Fully convert touched files to idiomatic Kotlin. Do not leave hybrid Java `AsyncTask` workarounds.
2. **Structured Concurrency & Scopes**:
   * **LifecycleOwner (Activities / Fragments)**: Use `activity.lifecycleScope` directly.
   * **ViewModels**: Use `viewModelScope`.
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
| **Phase 2** | Tier 2 | `PurgeAccountActivity` (`PurgeAccountTask`)<br>`RateNode` (`RateDownloadTask`)<br>`ExchangeRatesListActivity` (`RatesDownloadTask`) | Low–Medium | Single-screen tasks with simple progress dialogs / loading indicators | **PLANNED** |
| **Phase 3** | Tier 3 | `PlannerActivity` (`PlannerTask`)<br>`MonthlyViewActivity` (`MonthlyPreviewTask`) | Medium | Screen data-loading; benefits from ViewModel + StateFlow pattern | **PLANNED** |
| **Phase 4** | Tier 4 | `AbstractTotalsDetailsActivity` (`CalculateAccountsTotalsTask`)<br>`BudgetListActivity` (`BudgetTotalsCalculationTask`) | Medium–High | Complex calculations, aggregation queries, and list adapters | **PLANNED** |
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

### Phase 2: Tier 2 – Single Activity Background Operations (NEXT)

* **Goal**: Migrate self-contained background tasks in single screens/widgets that display progress dialogs or UI indicators.
* **Target Files & Conversions**:
  1. **`PurgeAccountActivity.java` -> `PurgeAccountActivity.kt`**:
     * Task: `PurgeAccountTask extends AsyncTask<Void, Void, Exception>` (lines 147–194).
     * Action: Convert activity to Kotlin. Show progress dialog, execute `DatabaseExport` (if requested) and `db.purgeAccountAtDate(accountId, date)` on `withContext(Dispatchers.IO)`. Handle exceptions and update UI on Main.
  2. **`RateNode.java` -> `RateNode.kt`**:
     * Task: `RateDownloadTask extends AsyncTask<Currency, Void, Pair<Boolean, RateResult>>` (lines 133–180).
     * Action: Convert widget to Kotlin. Launch download coroutine tied to View lifecycle/scope, call `RateDownloader` on `Dispatchers.IO`, update rate input view on completion, and handle cancellation.
  3. **`ExchangeRatesListActivity.java` -> `ExchangeRatesListActivity.kt`**:
     * Task: `RatesDownloadTask extends AsyncTask<RateNode, Void, Void>` (lines 229–285).
     * Action: Convert activity to Kotlin. Launch download with `lifecycleScope`, show progress dialog, download latest exchange rates on `Dispatchers.IO`, and refresh list adapter on Main.
* **Verification**:
  * Unit tests: `./gradlew :app:testDebugUnitTest`
  * Manual: Test account purge dialog, test currency rate download inside transaction editor (`RateNode`), test batch exchange rate download from exchange rate list.

---

### Phase 3: Tier 3 – Screen Data Loaders (ViewModel + StateFlow)

* **Goal**: Modernize screen data fetching into reactive MVVM/MVI with `ViewModel` and `StateFlow`.
* **Target Files & Conversions**:
  1. **`PlannerActivity.java` -> `PlannerActivity.kt` (+ `PlannerViewModel.kt`)**:
     * Task: `PlannerTask extends AsyncTask<Criteria, Void, List<TransactionPlan>>` (lines 146–180).
     * Action: Extract planning calculations into `PlannerViewModel`. Load transaction plans asynchronously using `viewModelScope` on `Dispatchers.IO`. Expose `StateFlow<PlannerUiState>` and collect with `repeatOnLifecycle` in the activity.
  2. **`MonthlyViewActivity.java` -> `MonthlyViewActivity.kt` (+ `MonthlyViewViewModel.kt`)**:
     * Task: `MonthlyPreviewTask extends AsyncTask<Void, Void, MonthlyViewResult>` (lines 365–420).
     * Action: Extract monthly aggregation queries and statement calculations into a ViewModel. Use `StateFlow` to drive calendar/monthly grid views, eliminating manual progress dialogs.
* **Verification**:
  * Unit tests: `./gradlew :app:testDebugUnitTest`
  * Manual: Verify transaction schedule projection in Planner; test monthly statement navigation and calendar rendering.

---

### Phase 4: Tier 4 – Complex Totals Calculations

* **Goal**: Migrate multi-criteria total calculation tasks that update tab/summary counters.
* **Target Files & Conversions**:
  1. **`AbstractTotalsDetailsActivity.java` -> `AbstractTotalsDetailsActivity.kt`**:
     * Task: `CalculateAccountsTotalsTask extends AsyncTask<Void, Void, List<Total>>` (lines 80–135).
     * Action: Convert activity hierarchy to Kotlin. Run database balance and currency aggregation queries on `Dispatchers.IO`. Update total header cards and account breakdown on Main.
  2. **`BudgetListActivity.java` -> `BudgetListActivity.kt`**:
     * Task: `BudgetTotalsCalculationTask extends AsyncTask<Void, Void, List<BudgetTotal>>` (lines 221–255).
     * Action: Migrate background calculation to Coroutines / Flow. Eliminate legacy `Handler` communication in `BudgetsTotalCalculator`.
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
     * Consumed by: `AccountListActivity`, `BlotterActivity`, `BudgetListActivity`, `EntityListActivity`, `ScheduledListActivity`.
     * Migration Strategy: Replace base class with a domain use-case / repository function `CalculateTotalUseCase` emitting via `Flow<TotalResult>` or suspend function called from `ViewModel`/`lifecycleScope`.
  2. **`ImportExportAsyncTask.java` Base Hierarchy**:
     * Base class: Handles background DB transaction setup, progress dialog management, and `EventBus` posting.
     * Concrete implementations (9 tasks):
       * `BackupExportTask`
       * `BackupImportTask`
       * `CsvExportTask`
       * `CsvImportTask`
       * `QifExportTask`
       * `QifImportTask`
       * `DropboxBackupTask`
       * `DropboxRestoreTask`
       * `GoogleDriveTask` / Online sync tasks
     * Migration Strategy: Refactor into dedicated repository/service classes (`BackupRepository`, `CsvService`, `QifService`) returning `Result<T>` or emitting `Flow<ProgressUpdate>`. Update all invoking dialogs and activities.
* **Verification**:
  * Full regression test suite: `./gradlew :app:testDebugUnitTest`
  * Complete backup/restore, CSV import/export, and QIF import/export testing across local storage and cloud providers.
