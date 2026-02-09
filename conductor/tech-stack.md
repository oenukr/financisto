# Technology Stack: Financisto

## Core Technologies
- **Programming Languages:** Primary development in **Kotlin**, with existing legacy components in **Java**.
- **Platform:** **Android**, targeting modern features while maintaining broad compatibility.
- **Build System:** **Gradle** using the **Kotlin DSL** for flexible and type-safe build configurations.

## Frameworks and Libraries
- **UI Framework:** **Jetpack Compose** (primary for new development) and legacy **Android XML/Activities**.
- **Dependency Injection:** **Koin** for lightweight and idiomatic dependency management in Kotlin.
- **Networking:** **Ktor** with the **CIO** engine for efficient and asynchronous HTTP requests.
- **Data Persistence:**
    - **SQLite:** Legacy database for structured financial data.
    - **DataStore (Preferences):** Modern solution for storing application settings and small key-value pairs.
- **Logging:** **Timber** for robust and extensible logging across the application.

## Quality Assurance and Tooling
- **Testing:** **JUnit 4** for unit testing, with **Kover** for code coverage analysis.
- **Linting and Formatting:** **Detekt** to enforce code quality, style consistency, and formatting.
- **Dependency Management:** **Gradle Version Catalogs (`libs.versions.toml`)** for centralized dependency versioning.
