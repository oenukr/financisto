# Specification: Replace `rfc2445-no-joda` with `lib-recur`

## Overview
This track involves migrating the recurrence rule (RRULE) handling logic in Financisto from the legacy `rfc2445-no-joda` library to the modern `lib-recur` by dmfs. The migration will follow a structured approach by first abstracting the recurrence logic behind a clean interface, enabling a safe swap of the underlying implementation. This ensures better testability, maintainability, and adherence to SOLID principles.

## Functional Requirements
1.  **Abstraction Layer:**
    *   Define a generic interface (e.g., `RecurrenceProcessor` or `RecurrenceIteratorFactory`) that decouples the app's core logic from the specific recurrence library.
    *   Refactor existing code to use this interface instead of direct library calls.
2.  **Library Integration:**
    *   Add `lib-recur` (dmfs) as a project dependency.
    *   Implement the new interface using `lib-recur`.
3.  **Code Refactoring & Conversion:**
    *   Rewrite key recurrence classes (`RecurrencePattern`, `Recurrence`, etc.) from Java to Kotlin where necessary to support the new architecture and improve code quality.
    *   Refactor `DateRecurrenceIterator.kt` to adapt the new library's iterator.
4.  **Cleanup:**
    *   Remove the `rfc2445-no-joda` library (JAR and references) once the new implementation is verified.

## Non-Functional Requirements
1.  **Architecture & Design:**
    *   **SOLID Principles:** Strictly adhere to SOLID principles.
    *   **Composition over Inheritance:** Favor composition; avoid deep inheritance hierarchies for the new components.
    *   **Kotlin First:** Prefer rewriting legacy Java classes in Kotlin when modifying them significantly.
2.  **Test Parity:** The new implementation must behave identically to the old one regarding date calculations.
3.  **Performance:** No noticeable degradation in calculation speed.

## Acceptance Criteria
1.  Recurrence logic is accessed via a library-agnostic interface.
2.  `rfc2445-no-joda` is completely removed.
3.  `lib-recur` is successfully integrated and operational.
4.  **Unit Tests Pass:** Tests verify the abstraction layer and the `lib-recur` implementation (verify patterns: Daily, Weekly, Monthly, etc.).
5.  **Integration Tests Pass:** `RecurrenceScheduler` schedules transactions correctly using the new implementation.
6.  **Migration Tests Pass:** Existing database-format RRULEs are successfully processed.

## Out of Scope
*   Adding new types of recurrence not currently supported.
*   Redesigning the `RecurrenceActivity` UI (unless required by the underlying data change).
