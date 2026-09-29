---
name: asynctask-to-coroutine
description: Migrates legacy Android AsyncTask implementations (in Java or Kotlin) to modern Kotlin Coroutines, ViewModel (viewModelScope), Flow/StateFlow, or suspend functions with structured concurrency. Use when refactoring legacy background tasks, removing deprecated AsyncTask references, converting Java/Kotlin AsyncTasks to coroutines, or modernizing asynchronous UI state management.
---

# AsyncTask to Kotlin Coroutines Migration

Migrate legacy Android `AsyncTask` implementations (Java or Kotlin) to modern Kotlin Coroutines, Kotlin Flow, and Android Jetpack Architecture components (`ViewModel`, `StateFlow`).

This skill incorporates the proven migration patterns used in Financisto (such as commit `74ccf22` replacing `ReportActivity`'s `ReportAsyncTask` and `PieChartGeneratorTask` with `ReportViewModel` and `PieChartViewModel`).

---

## When to Use

- Refactoring or modernizing legacy `AsyncTask` classes (`extends AsyncTask<Params, Progress, Result>`).
- Resolving memory leaks caused by `AsyncTask` retaining implicit or explicit references to an `Activity`, `Context`, `View`, or `Dialog`.
- Migrating background database queries, file I/O, backup/restore, import/export, or network calls to structured concurrency.
- Converting legacy UI loading indicators and result callbacks to reactive `StateFlow` or Jetpack Compose state.

---

## Core Concept Mapping

| `AsyncTask` Component | Kotlin Coroutine / Architecture Equivalent | Responsibility / Notes |
| :--- | :--- | :--- |
| `Params` | Function parameters or ViewModel properties | Arguments required to execute the task |
| `Progress` | `Flow<T>` or `MutableStateFlow<Progress>` | Streaming intermediate progress updates to UI |
| `Result` | Return value of `suspend` function / `StateFlow<UiState<T>>` | Final outcome produced by the background operation |
| `onPreExecute()` | Setting initial loading state (`_uiState.value = UiState.Loading`) | Runs on Main thread before initiating work |
| `doInBackground(vararg params)` | `withContext(Dispatchers.IO)` / `withContext(Dispatchers.Default)` | Executes heavy I/O or CPU work off the Main thread |
| `publishProgress(values...)` | `emit(progress)` on a `FlowCollector` | Emits progress events from background work |
| `onProgressUpdate(values...)` | Collecting progress `Flow` or observing progress state | Updates UI elements (e.g. progress bar, message text) on Main thread |
| `onPostExecute(result)` | Emitting `UiState.Success(data)` / `UiState.Error(e)` | Delivers final result to UI on Main thread |
| `cancel(mayInterrupt)` | `job.cancel()` & cooperative cancellation checks | Cancels the coroutine and halts cooperative loops |
| `isCancelled()` | `currentCoroutineContext().isActive` or `ensureActive()` | Checks if the current task/job has been cancelled |
| `execute()` / `executeOnExecutor()` | `viewModelScope.launch { ... }` / `lifecycleScope.launch { ... }` | Launches the coroutine within a lifecycle-bounded scope |

---

## Architecture Migration Patterns

### Pattern A: ViewModel + Coroutines + StateFlow (Recommended for UI Screens)

**Best for:** Inner `AsyncTask`s inside `Activity` or `Fragment` that load data for display.
- Encapsulates state and survives configuration changes (rotation).
- Uses `viewModelScope.launch` which cancels automatically when ViewModel clears.
- Thread-safe: Exposes immutable `StateFlow<UiState<T>>` to the View/Compose layer.

### Pattern B: Repository / Suspend Function + Flow (Best for Data/Business Logic)

**Best for:** Standalone tasks like DB import/export, file parsing, backups, or calculation helpers (e.g. `BackupExportTask`, `CsvImportTask`, `TotalCalculationTask`).
- Pure suspend functions decoupled from UI classes.
- Uses `Flow<Progress>` for streaming progress updates without leaking `Context` or `ProgressDialog`.
- Accepts a `CoroutineDispatcher` (defaulting to `Dispatchers.IO`) for dependency injection and testability.

### Pattern C: WorkManager + CoroutineWorker (Guaranteed Background Work)

**Best for:** Tasks that must finish even if the user exits the app or the process dies (e.g. scheduled auto-backup).

---

## Workflow Steps

### 1. Analyze the Existing AsyncTask

Identify all components of the target task:
1. **Inputs & Generics**: Check `<Params, Progress, Result>` types and constructor arguments.
2. **Context & View References**: Does the task hold direct or `WeakReference` references to `Activity`, `Context`, `TextView`, `ListView`, or `ProgressDialog`?
3. **Execution Type**:
   - Disk/Database/Network -> Needs `Dispatchers.IO`.
   - CPU calculation/Parsing/Geometry -> Needs `Dispatchers.Default`.
4. **Cancellation**: Does the caller call `cancel(true)` or check `isCancelled()`?
5. **UI Side Effects**: What views are updated in `onPreExecute()`, `onProgressUpdate()`, and `onPostExecute()`?

---

### 2. Verify Dependencies

Ensure the module's `build.gradle.kts` has the coroutine and lifecycle dependencies:

```kotlin
// Coroutines
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.coroutines.android)

// Lifecycle & ViewModel (if using Pattern A)
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
```

---

### 3. Model UI & Progress States

Define clear, exhaustive state representations using sealed interfaces:

```kotlin
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String?, val cause: Throwable? = null) : UiState<Nothing>
}

data class ProgressUpdate(val current: Int, val total: Int, val message: String? = null)
```

---

### 4. Implement Coroutine Logic

#### Pattern A Implementation (ViewModel)

```kotlin
class DataReportViewModel(
    private val database: DatabaseAdapter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ReportData>>(UiState.Idle)
    val uiState: StateFlow<UiState<ReportData>> = _uiState.asStateFlow()

    private var executionJob: Job? = null

    fun loadData(filter: WhereFilter) {
        // Cancel any existing execution before starting a new one
        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Background I/O execution
                val rawData = withContext(ioDispatcher) {
                    database.queryData(filter)
                }
                
                // Heavy computation (if needed)
                val processedData = withContext(defaultDispatcher) {
                    processReport(rawData)
                }

                _uiState.value = UiState.Success(processedData)
            } catch (e: CancellationException) {
                // Must rethrow CancellationException for structured concurrency
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unknown error occurred", e)
            }
        }
    }
}
```

#### Pattern B Implementation (Flow with Progress)

```kotlin
class BackupRepository(
    private val db: DatabaseAdapter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun exportBackup(targetFile: File): Flow<ProgressUpdate> = flow {
        emit(ProgressUpdate(current = 0, total = 100, message = "Preparing export..."))
        
        withContext(ioDispatcher) {
            val totalSteps = 100
            for (step in 1..totalSteps) {
                // Cooperative cancellation check
                currentCoroutineContext().ensureActive()

                // Perform chunk of work
                processChunk(step)

                emit(ProgressUpdate(current = step, total = totalSteps, message = "Step $step of $totalSteps"))
            }
        }
    }.flowOn(ioDispatcher)
}
```

---

### 5. Update UI Layer (Activity / Fragment / Compose)

#### In Jetpack Compose:
```kotlin
@Composable
fun ReportScreen(viewModel: DataReportViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is UiState.Idle -> Unit
        is UiState.Loading -> CircularProgressIndicator()
        is UiState.Success -> ReportList(data = state.data)
        is UiState.Error -> ErrorBanner(message = state.message)
    }
}
```

#### In Android Views (ViewBinding / Activity):
```kotlin
class ReportActivity : AppCompatActivity() {
    private val viewModel: DataReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBar.isVisible = state is UiState.Loading
                    when (state) {
                        is UiState.Success -> adapter.submitList(state.data.items)
                        is UiState.Error -> showErrorDialog(state.message)
                        else -> Unit
                    }
                }
            }
        }
    }
}
```

---

### 6. Delete Legacy AsyncTask & Verify

1. Remove the legacy `AsyncTask` class or mark it `@Deprecated(message = "Migrated to Coroutines", replaceWith = ...)` if public library code.
2. Verify that no references or callbacks (`ImportExportAsyncTaskListener`, etc.) remain.
3. Build and run unit tests: `./gradlew test` (or target test task).

---

## Case Study: Financisto Reference (Commit `74ccf22`)

In Financisto commit `74ccf22` (`Issue 51 replace internal chart solution (#64)`):

### Legacy (`ReportActivity.java`):
- `ReportAsyncTask extends AsyncTask<Void, Void, ReportData>`
  - Held direct references to `db`, `ReportActivity.this`, and modified Views in `onPreExecute()` and `onPostExecute()`.
  - Manual cancellation tracking via `cancelCurrentReportTask()`.
- `PieChartGeneratorTask extends AsyncTask<Void, Void, Intent>`
  - Generated pie charts off-thread, manipulated indeterminate progress bar on UI thread.

### Modern Replacement:
- Created [ReportViewModel.kt](file:///Users/ogopanchuk/development/git/financisto/app/src/main/java/ru/orangesoftware/financisto/reports/ReportViewModel.kt):
  - Extracted database access into `suspend fun calculateReport(): ReportData?` running on `withContext(Dispatchers.IO)`.
  - Dispatched UI updates to `_reportTotal` and `_graphUnits` using `StateFlow`.
- Created [PieChartViewModel.kt](file:///Users/ogopanchuk/development/git/financisto/app/src/main/java/ru/orangesoftware/financisto/reports/PieChartViewModel.kt):
  - Separated I/O data loading (`Dispatchers.IO`) from color and geometry calculations (`withContext(Dispatchers.Default)`).
  - Exposes `StateFlow<List<ChartData>>` for Jetpack Compose UI consumption.

---

## Pitfalls to Avoid

- **Never Leak Context or Views**: Never pass an `Activity`, `View`, or `Dialog` into a `ViewModel` or `suspend` function.
- **Never Swallow `CancellationException`**: If catching `Exception`, always rethrow `CancellationException` to allow coroutine cancellation to propagate.
- **Avoid GlobalScope**: Never use `GlobalScope.launch`. Use `viewModelScope`, `lifecycleScope`, or an injected `CoroutineScope` with a supervised lifecycle.
- **Cooperative Cancellation**: Coroutines are cooperative. In tight or long loops, call `ensureActive()` or `yield()` to allow timely cancellation.
- **Hardcoded Dispatchers**: Avoid hardcoding `Dispatchers.IO` directly inside classes without constructor injection if unit testing is required.

---

## Verification Checklist

- [ ] Target `AsyncTask` replaced without breaking existing caller contracts.
- [ ] Threading split properly: I/O on `Dispatchers.IO`, CPU work on `Dispatchers.Default`.
- [ ] No `Activity`, `Context`, `View`, or `Dialog` references held in background scope.
- [ ] Cancellation is cooperative and `CancellationException` is properly rethrown.
- [ ] UI safely collects state using lifecycle-aware collectors (`repeatOnLifecycle` or `collectAsStateWithLifecycle`).
- [ ] Local unit tests pass (`./gradlew test`).
