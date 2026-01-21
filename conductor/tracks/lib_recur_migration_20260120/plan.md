# Implementation Plan: Replace `rfc2445-no-joda` with `lib-recur`

## Phase 1: Infrastructure & Abstraction (TDD)
- [x] Task: Update dependencies [b78ce62]
    - [ ] Add `lib-recur` to `libs.versions.toml`
    - [ ] Add `lib-recur` to `app/build.gradle.kts`
    - [ ] Sync Gradle
- [ ] Task: Define Recurrence Abstraction
    - [ ] Write failing tests for `RecurrenceProcessor` interface requirements
    - [ ] Create `RecurrenceProcessor.kt` interface (Library-agnostic)
    - [ ] Create `RecurrenceResult.kt` data class for next date calculations
- [ ] Task: Implement Legacy Wrapper (Optional/Transition)
    - [ ] Create `LegacyRecurrenceProcessor` (wrapping `rfc2445-no-joda`) to verify the abstraction works with existing logic.
    - [ ] Update `DateRecurrenceIterator.kt` to use the new abstraction.
- [ ] Task: Conductor - User Manual Verification ' Phase 1: Infrastructure & Abstraction (TDD)' (Protocol in workflow.md)

## Phase 2: Implementation of `lib-recur` (TDD)
- [ ] Task: Create `LibRecurProcessor`
    - [ ] Write failing tests for `LibRecurProcessor` using various RRULE patterns.
    - [ ] Implement `LibRecurProcessor.kt` using `dmfs/lib-recur`.
    - [ ] Ensure parity in date calculation with `LegacyRecurrenceProcessor`.
- [ ] Task: Convert `Recurrence` and `RecurrencePattern` to Kotlin
    - [ ] Write failing tests for `Recurrence` logic in Kotlin.
    - [ ] Convert `Recurrence.java` to `Recurrence.kt`.
    - [ ] Convert `RecurrencePattern.java` to `RecurrencePattern.kt`.
    - [ ] Convert `RecurrencePeriod.java` to `RecurrencePeriod.kt`.
- [ ] Task: Integrate `LibRecurProcessor`
    - [ ] Switch the default `RecurrenceProcessor` implementation to `LibRecurProcessor`.
    - [ ] Verify all existing unit tests pass.
- [ ] Task: Conductor - User Manual Verification ' Phase 2: Implementation of lib-recur (TDD)' (Protocol in workflow.md)

## Phase 3: Verification & Cleanup
- [ ] Task: Integration & Migration Testing
    - [ ] Write integration tests for `RecurrenceScheduler` with the new implementation.
    - [ ] Write migration tests using sample RRULE strings from the database.
- [ ] Task: Library Removal
    - [ ] Remove `app/libs/rfc2445-no-joda.jar`.
    - [ ] Remove any remaining references to `com.google.ical` in the codebase.
    - [ ] Final project build and `detekt` check.
- [ ] Task: Conductor - User Manual Verification ' Phase 3: Verification & Cleanup' (Protocol in workflow.md)
