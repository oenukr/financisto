# Implementation Plan: Replace `rfc2445-no-joda` with `lib-recur`

## Phase 1: Infrastructure & Abstraction (TDD) [checkpoint: 98b74e4]
- [x] Task: Update dependencies [b78ce62]
- [x] Task: Define Recurrence Abstraction [05c82ff]
- [x] Task: Implement Legacy Wrapper (Optional/Transition) [d937f0b]
- [x] Task: Conductor - User Manual Verification ' Phase 1: Infrastructure & Abstraction (TDD)' (Protocol in workflow.md)

## Phase 2: Implementation of `lib-recur` (TDD) [checkpoint: 160922b]
- [x] Task: Create LibRecurProcessor [f98eecb]
    - [ ] Write failing tests for `LibRecurProcessor` using various RRULE patterns.
    - [ ] Implement `LibRecurProcessor.kt` using `dmfs/lib-recur`.
    - [ ] Ensure parity in date calculation with `LegacyRecurrenceProcessor`.
- [x] Task: Convert Recurrence and RecurrencePattern to Kotlin [f979a25]
    - [ ] Write failing tests for `Recurrence` logic in Kotlin.
    - [ ] Convert `Recurrence.java` to `Recurrence.kt`.
    - [ ] Convert `RecurrencePattern.java` to `RecurrencePattern.kt`.
    - [ ] Convert `RecurrencePeriod.java` to `RecurrencePeriod.kt`.
- [x] Task: Integrate LibRecurProcessor [6f2ccc5]
    - [ ] Switch the default `RecurrenceProcessor` implementation to `LibRecurProcessor`.
    - [ ] Verify all existing unit tests pass.
- [x] Task: Conductor - User Manual Verification ' Phase 2: Implementation of lib-recur (TDD)' (Protocol in workflow.md) [160922b]

## Phase 3: Verification & Cleanup
- [x] Task: Integration & Migration Testing [eac74a6]
    - [ ] Write integration tests for `RecurrenceScheduler` with the new implementation.
    - [ ] Write migration tests using sample RRULE strings from the database.
- [ ] Task: Library Removal
    - [ ] Remove `app/libs/rfc2445-no-joda.jar`.
    - [ ] Remove any remaining references to `com.google.ical` in the codebase.
    - [ ] Final project build and `detekt` check.
- [ ] Task: Conductor - User Manual Verification ' Phase 3: Verification & Cleanup' (Protocol in workflow.md)
