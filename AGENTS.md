# Financisto AGENTS.md

## Project Overview
**Financisto** is an open-source personal finance tracker for the Android platform. It is a feature-rich application that allows users to manage multiple accounts, currencies, budgets, and transactions.

### Key Features
- **Account Management:** Multiple accounts and currencies.
- **Transactions:** Transfers, scheduled/recurring transactions, and split transactions.
- **Budgets:** Hierarchical categories and recurring budgets.
- **Data Management:** QIF/CSV import/export and automatic daily backups.
- **Cloud Integration:** Backup to Dropbox and Google Drive.
- **Reporting:** Filtering and visual reporting (charts).

### Tech Stack
- **Languages:** Primarily Kotlin, with legacy components in Java.
- **Build System:** Gradle (Kotlin DSL).
- **Architecture:** Transitioning from a multi-activity legacy architecture to a modern setup.
- **UI:** Mixed; transitioning towards Jetpack Compose, with many legacy XML-based Activities.
- **Dependency Injection:** [Koin](https://insert-koin.io/) (configured in `JavaAppKoin.kt`).
- **Networking:** [Ktor](https://ktor.io/) with CIO engine.
- **Logging:** [Timber](https://github.com/JakeWharton/timber).
- **Data Persistence:** SQLite (legacy) and DataStore for preferences.
- **Linting:** [Detekt](https://detekt.dev/) - Identifies potential issues and style violations.
- **Testing:** JUnit 4, with [Kover](https://github.com/Kotlin/kotlinx-kover) for code coverage.
- **Code formatter:**  [Detekt](https://detekt.dev/) - Enforces consistent formatting automatically.

## Building and Running
Key Gradle tasks:
- **Clean:** `./gradlew clean`
- **Build (Debug):** `./gradlew assembleDebug`
- **Run Unit Tests:** `./gradlew test`
- **Run Linting:** `./gradlew detekt`
- **Generate Coverage Report:** `./gradlew koverHtmlReport`
- **Install Debug App:** `./gradlew app:installDebug`

## Project Structure
- `app/src/main/java`: Source code (Kotlin/Java).
- `app/src/main/res`: Android resources.
- `app/src/main/assets/database`: SQL scripts for database creation and migration.
- `shared/style-guidance.md`: Detailed coding style and principles.
- `config/detekt.yml`: Linting rules.
- `libs.versions.toml`: Dependency version management.


@./get-started.md

## Coding style

@./shared/style-guidance.md
