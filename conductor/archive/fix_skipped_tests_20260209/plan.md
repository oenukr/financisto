# Implementation Plan: fix_skipped_tests_20260209

This plan outlines the steps to resolve all 13 skipped unit tests in the Financisto project, addressing both Robolectric infrastructure issues and logic-related flakiness.

## Phase 1: Robolectric Infrastructure Fixes
Focus: Resolve "Need to tell robolectric to make the needed folder writable" issues by implementing proper temp folder handling.

- [x] **Task: Fix Database Backup & Restore Tests**
    - [x] Remove `@Ignore` from `DatabaseBackupTest`
    - [x] Implement `TemporaryFolder` or similar mechanism to handle writable paths in `DatabaseBackupTest`
    - [x] Verify `DatabaseBackupTest` passes
    - [x] Remove `@Ignore` from `LegacyDatabaseRestoreTest`
    - [x] Implement fix for writable paths in `LegacyDatabaseRestoreTest`
    - [x] Verify `LegacyDatabaseRestoreTest` passes
- [x] **Task: Fix Import/Export Tests**
    - [x] Remove `@Ignore` from `CsvImportTest`
    - [x] Implement fix for writable paths in `CsvImportTest`
    - [x] Verify `CsvImportTest` passes
    - [x] Remove `@Ignore` from `QifImportTestCases`
    - [x] Implement fix for writable paths in `QifImportTestCases`
    - [x] Verify `QifImportTestCases` passes
- [x] **Task: Conductor - User Manual Verification 'Phase 1: Robolectric Infrastructure Fixes' (Protocol in workflow.md)**

## Phase 2: Logic Stability & Flaky Tests
Focus: Analyze and fix the intermittent failure in `RunningBalanceTest`.

- [x] **Task: Investigate RunningBalanceTest Flakiness**
    - [x] Remove `@Ignore` from `RunningBalanceTest.should_update_running_balance_for_two_accounts_when_updating_transfer_split`
    - [x] Create a reproduction script/loop to confirm the intermittent failure (e.g., run 100 times)
    - [x] Analyze the logs and state to identify the root cause of the "600 instead of 800" error
- [x] **Task: Implement Fix for RunningBalanceTest**
    - [x] Apply fix to the running balance calculation logic
    - [x] Verify the fix by running the test 100 times without failure
- [x] **Task: Conductor - User Manual Verification 'Phase 2: Logic Stability & Flaky Tests' (Protocol in workflow.md)**

## Phase 3: Final Verification & Documentation
Focus: Ensure project-wide stability and document environment requirements.

- [x] **Task: Full Test Suite Verification**
    - [x] Run `./gradlew test` and ensure 100% pass rate with no skips
    - [x] Run `./gradlew detekt` to ensure code style compliance
- [x] **Task: Documentation & Cleanup**
    - [x] Add internal documentation (e.g., in a `TESTING.md` or as class-level comments) explaining the Robolectric writable folder configuration
    - [x] Ensure all temporary artifacts created during tests are properly cleaned up
- [x] **Task: Conductor - User Manual Verification 'Phase 3: Final Verification & Documentation' (Protocol in workflow.md)**
