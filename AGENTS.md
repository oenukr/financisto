# Financisto AGENTS.md

## 🤖 Agent Directives
*You are assisting with Financisto, an open-source personal finance tracker for Android. When writing or modifying code, strictly adhere to these rules:*

- **Language:** Always use Kotlin for new files. Do not write new Java code. When modifying legacy Java files, keep changes localized or offer to convert them to Kotlin if the change is extensive.
- **UI Paradigm:** Use Jetpack Compose for all new UI components. Only modify legacy XML layouts if fixing a bug in an existing screen.
- **Architecture:** We are transitioning from a legacy multi-activity architecture to MVI with unidirectional data flow. Apply the modern approach to all new features.
- **Dependency Injection:** Use Koin. Refer to `JavaAppKoin.kt` for existing module configurations.
- **Database:** We use legacy SQLite with SQL scripts in `assets/database`. Do not modify any SQL as of now.
- **State Management:** Use StateFlow in ViewModels to expose UI state to Compose.

## 🧠 Dynamic Agent Skills
We maintain specific operational procedures and expert personas in the `.agent/skills/` directory. Do not load these unless relevant to the user's current request.

**Instruction:** Before executing a task or writing code, review the trigger conditions below. If your task matches a trigger, you MUST read the corresponding skill file to gather your operational directives before proceeding.

### Available Skills & Triggers:
* **Trigger:** The user asks to upgrade the build system, modify `build.gradle.kts`, or specifically mentions AGP 9.
    * **Action:** Read `@./.agent/skills/agp-9-upgrade/SKILL.md`
* **Trigger:** The task requires running terminal commands, using `adb`, analyzing device logs, or executing Gradle tasks.
    * **Action:** Read `@./.agent/skills/android-cli/SKILL.md`
* **Trigger:** The user asks to update the UI, modernize a screen, or explicitly mentions converting legacy layouts.
    * **Action:** Read `@./.agent/skills/migrate-xml-views-to-jetpack-compose/SKILL.md`
* **Trigger:** The task involves fixing app crashes related to minification, modifying `proguard-rules.pro`, or analyzing APK size.
    * **Action:** Read `@./.agent/skills/r8-analyzer/SKILL.md`

---

## 📖 Project Overview
Financisto allows users to manage multiple accounts, currencies, budgets, and transactions (transfers, scheduled, split). It supports QIF/CSV imports/exports, cloud backups (Dropbox/Google Drive), and visual reporting.

## 🛠 Tech Stack
- **Languages:** Kotlin (Primary), Java (Legacy).
- **Build System:** Gradle (Kotlin DSL).
- **UI:** Jetpack Compose (Modern), XML (Legacy).
- **Dependency Injection:** [Koin](https://insert-koin.io/).
- **Networking:** [Ktor](https://ktor.io/) (CIO engine).
- **Logging:** [Timber](https://github.com/JakeWharton/timber).
- **Data Persistence:** SQLite (legacy transactions/accounts) and DataStore (preferences).
- **Static Analysis:** [Detekt](https://detekt.dev/) - Handles both linting and automatic code formatting.
- **Testing:** JUnit 4, mockito. Code coverage via [Kover](https://github.com/Kotlin/kotlinx-kover).

## 🚀 Building and Running
- **Clean:** `./gradlew clean`
- **Build (Debug):** `./gradlew assembleDebug`
- **Run Unit Tests:** `./gradlew test`
- **Run Linting/Formatting:** `./gradlew detekt`
- **Generate Coverage Report:** `./gradlew koverHtmlReport`
- **Install Debug App:** `./gradlew app:installDebug`

## 📂 Project Structure
- `app/src/main/java`: Source code.
- `app/src/main/res`: Android resources (legacy XML layouts, values, drawables).
- `app/src/main/assets/database`: SQL scripts for DB creation/migration.
- `config/detekt.yml`: Linting and formatting rules.
- `gradle/libs.versions.toml`: Dependency version management.

## 📚 Extended Context
Read these files for further context before generating extensive logic:
@./get-started.md
@./shared/style-guidance.md
