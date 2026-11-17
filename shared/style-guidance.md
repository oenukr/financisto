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
- **Architecture & State:** Follow Unidirectional Data Flow (MVI/MVVM). Expose UI state from ViewModels using `StateFlow`.
- **Asynchronous Code:** Use Kotlin Coroutines and `viewModelScope` for background work instead of legacy `AsyncTask` or raw threads.
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
- **Graceful Failures:** Expose errors to UI state (e.g. sealed error states in `StateFlow`) rather than crashing or swallowing errors silently.

## Comments & Documentation
- **Explain "Why", Not "What":** Write clear and concise comments explaining the intent and reasoning behind non-trivial code.
- **Self-Documenting Code:** Write expressive code so comments are used sparingly.
- **Sentence Case:** Start comments with a capital letter and use proper punctuation.
