# SOLID Principles Compliance Report

This document outlines the findings regarding the compliance of the `financisto` project with SOLID principles.

## 1. Single Responsibility Principle (SRP)

**Violation:** Multiple classes act as "God Objects," handling distinct and unrelated responsibilities.

*   **`ru.orangesoftware.financisto.db.DatabaseAdapter`**: This class is a primary offender. It handles:
    *   CRUD operations for all entity types (Accounts, Transactions, Categories, etc.).
    *   Complex business logic (e.g., `duplicateTransaction`, `purgeAccountAtDate`).
    *   Reporting queries and calculations (`getAccountsTotal`).
    *   Database transaction management.
    *   *Recommendation:* Break this class down into separate DAO (Data Access Object) classes or Repositories for each entity (e.g., `AccountRepository`, `TransactionRepository`). Move business logic to Use Case/Interactor classes or Domain Services.

*   **`ru.orangesoftware.financisto.activity.TransactionActivity`**:
    *   Manages UI state and user interactions.
    *   Contains complex business logic for split adjustments and transaction updates.
    *   Performs direct data access and object serialization.
    *   *Recommendation:* Adopt a presentation pattern like MVVM or MVP. Move logic to a `ViewModel` or `Presenter`. Delegate data access to Repositories.

*   **`ru.orangesoftware.financisto.activity.BlotterActivity`**:
    *   Handles UI for transaction lists, filtering, and menus.
    *   Manages navigation results from multiple other activities.
    *   Contains business logic for duplication and template creation.
    *   *Recommendation:* Similar to `TransactionActivity`, migrate logic to a `ViewModel` and separate navigation logic.

## 2. Open/Closed Principle (OCP)

**Violation:** Classes are not easily extensible without modification.

*   **`ru.orangesoftware.financisto.export.Export`**:
    *   Contains hardcoded methods for specific cloud providers (`uploadBackupFileToDropbox`, `uploadBackupFileToGoogleDrive`).
    *   Adding a new cloud storage provider (e.g., OneDrive) requires modifying this base class.
    *   *Recommendation:* Create an abstraction (interface) for `StorageProvider` (e.g., `uploadFile(File file)`) and inject implementations into the `Export` process.

## 3. Liskov Substitution Principle (LSP)

**Potential Violation:**
*   **`DatabaseAdapter` extending `MyEntityManager`**: If `DatabaseAdapter` overrides methods from `MyEntityManager` in a way that changes the expected behavior or if clients strictly rely on `DatabaseAdapter` specifics where `MyEntityManager` should suffice, this is a violation. The massive expansion of functionality in the subclass suggests it's not really substitutable for the parent in a meaningful way for most operations.

## 4. Interface Segregation Principle (ISP)

**Violation:** Large interfaces force clients to depend on methods they do not use.

*   **`DatabaseAdapter` (Implicit Interface)**: Clients that only need to read `Account` data are forced to depend on a class that also handles `Transaction` writes, `Category` management, and report generation.
    *   *Recommendation:* Extract specific interfaces (e.g., `AccountReader`, `TransactionWriter`) so consumers only depend on what they strictly need.

## 5. Dependency Inversion Principle (DIP)

**Violation:** High-level modules depend on low-level modules or concrete implementations instead of abstractions.

*   **Service Locator Pattern (`DependenciesHolder`)**:
    *   Classes like `TransactionActivity` and `Export` directly instantiate or statically access `DependenciesHolder` to get dependencies. This couples the classes to the specific container and makes unit testing difficult (requires mocking the static holder).
    *   *Recommendation:* Use a proper Dependency Injection framework (like Hilt or Koin, which is already present in the project) to inject dependencies through constructors or fields. Ensure high-level Use Cases depend on Repository interfaces, not the concrete `DatabaseAdapter`.

*   **Direct Instantiation**:
    *   Activities often instantiate helper classes (e.g., `Utils`, `SplitAdjuster`) directly, creating tight coupling.

## Summary

The project exhibits significant technical debt related to SOLID principles, particularly regarding SRP and DIP. The "God Class" anti-pattern is prevalent in the database and main activity layers. Refactoring towards a clean architecture (e.g., MVVM + Clean Architecture) with proper Dependency Injection would significantly improve maintainability and testability.
