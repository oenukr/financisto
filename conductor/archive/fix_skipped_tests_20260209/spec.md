# Specification: fix_skipped_tests_20260209

## Overview
This track aims to resolve all 13 currently skipped (`@Ignore`) unit tests in the Financisto project. These tests cover critical financial operations such as Database Backup/Restore, CSV/QIF Import/Export, and Running Balance calculations. Fixing these tests will improve code quality, ensure the reliability of core features, and provide a stable foundation for future development.

## Functional Requirements
- **Robolectric Fixes:** Resolve folder-writing permission issues in `LegacyDatabaseRestoreTest`, `DatabaseBackupTest`, `CsvImportTest`, and `QifImportTestCases` by implementing proper temporary file/folder management within the Robolectric test environment.
- **Logic Fix:** Analyze and resolve the intermittent failure in `RunningBalanceTest.should_update_running_balance_for_two_accounts_when_updating_transfer_split`.
- **Test Activation:** Remove all `@Ignore` annotations from the identified tests once they pass consistently.

## Non-Functional Requirements
- **Consistency:** Tests must pass reliably in both local development environments and CI (GitHub Actions).
- **Style:** Adhere to the project's testing conventions as seen in existing `JUnit 4` tests.
- **Documentation:** Add comments or update the internal test guide explaining how the Robolectric environment was configured to handle file system operations safely.

## Acceptance Criteria
- [ ] All 13 previously skipped tests are active (no `@Ignore`).
- [ ] The full test suite (`./gradlew test`) passes successfully with zero failures and zero skips.
- [ ] The flaky `RunningBalanceTest` passes in a multi-run loop (at least 10 consecutive passes).
- [ ] Documentation for the Robolectric environment fix is provided.

## Out of Scope
- Adding new feature tests beyond those already existing but skipped.
- Upgrading the major version of Robolectric or JUnit unless strictly required for the fix.
