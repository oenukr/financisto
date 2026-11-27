# Financisto Coding & Style Guide

## Introduction
This style guide outlines the coding conventions and architectural guidelines for Kotlin code in Financisto.
It is based on canonical Kotlin guidelines and aligned with the project's modern architecture goals (MVI, Compose, Koin, Coroutines).

## Key Principles
- **Readability:** Code should be easy to read and understand for all team members.
- **Maintainability:** Code should be easy to modify, test, and extend.
- **Consistency:** Adhering to a consistent style across all modules improves collaboration and reduces errors.
- **Performance:** While readability is paramount, code should be efficient and avoid unnecessary allocations.

## Architecture & Development Conventions
- **Language:** Use Kotlin for all new code. Avoid writing new Java code.
- **UI Paradigm:** Use Jetpack Compose for all new UI components. Modify legacy XML layouts only when fixing bugs in existing screens.
- **Architecture & State:** Follow Unidirectional Data Flow (MVI/MVVM). For Compose screens, use Jetpack AppState: `androidx.appstate.transform` exposing Compose `State<T>` with `SavedStateContainer` bridged to `SavedStateHandle`, `Snapshot.withMutableSnapshot` for atomic state writes, `androidx.appstate.statestore` with `PersistToDataStore` / `syncToDataStore` for shared cross-component state, and `transformTest` for ViewModel unit tests. Retain `StateFlow` for legacy XML ViewModels (`MonthlyViewViewModel`, `PlannerViewModel`) and `Channel`/`Flow` for one-shot effects.
- **Asynchronous Code:** Use Kotlin Coroutines instead of legacy `AsyncTask`, raw threads, or `GlobalScope`, selecting the coroutine scope based on work ownership: use `viewModelScope` for ViewModel-owned work, `lifecycleScope` for `LifecycleOwner`-bound work, and an appropriate longer-lived scope such as `applicationScope` (provided via Koin) for app-level work that must outlive a screen.
- **Dependency Injection:** Use Koin for DI. Register new dependencies in `JavaAppKoin.kt` or feature Koin modules. Prefer constructor injection over manual service location (`DependenciesHolder`).
- **Database:** Database schema and migrations are located in `app/src/main/assets/database`. Do not alter SQL scripts without architectural review.

## Coding Style & Kotlin Idioms
- **Consistency & Formatting:** Follow existing project patterns and ensure `./gradlew detekt` passes before committing.
- **Composition over Inheritance:** Prefer composition over inheritance wherever possible.
- **Null Safety:** Avoid the not-null assertion operator `!!`. Prefer safe calls (`?.`), Elvis operator (`?:`), scoped functions (`let`, `run`), or `requireNotNull()` / `checkNotNull()`.
- **Exception Catching:**
  - Catch specific exception types rather than broad exceptions (`Exception` / `Throwable`).
  - Use `runCatching` for simple local expressions, but **never swallow `CancellationException`** in coroutine contexts. If using `runCatching` inside coroutines, rethrow `CancellationException` or use explicit `try { ... } catch (e: SpecificException) { ... }`.

## Logging
- **Logger Interface:** Always use `Timber` via the `Logger` interface (injected via Koin) or directly via `Timber`.
- **Appropriate Levels:** Use standard log levels: `DEBUG` (`d`), `INFO` (`i`), `WARN` (`w`), `ERROR` (`e`), `VERBOSE` (`v`), or `WTF` (`wtf`).
- **Contextual Logging:** Include relevant parameters, identifiers, or timing information in log messages to aid debugging.

## Error Handling
- **Specific Exceptions:** Avoid catching broad exceptions like `Exception` or `Throwable`.
- **Graceful Failures:** Expose errors to UI state (e.g. sealed error states in Compose `State<T>` via `androidx.appstate.transform`, or `StateFlow` in legacy XML ViewModels) rather than crashing or swallowing errors silently.

## Comments & Documentation
- **Explain "Why", Not "What":** Write clear and concise comments explaining the intent and reasoning behind non-trivial code.
- **Self-Documenting Code:** Write expressive code so comments are used sparingly.
- **Sentence Case:** Start comments with a capital letter and use proper punctuation.
