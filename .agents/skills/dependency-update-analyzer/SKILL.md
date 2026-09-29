---
name: dependency-update-analyzer
description: Analyze changeset dependency and library updates, inspect changelogs and release notes for breaking/behavioral changes, evaluate codebase impact, and generate required code migrations. Use when reviewing dependency updates, checking library upgrades, analyzing changelogs, or migrating deprecated or broken APIs after version bumps.
---

# Dependency Update Analyzer

Analyze dependency changes in a codebase changeset, review upstream release notes and changelogs, evaluate the actual impact on the repository, and provide concrete migration code.

## When to Use

- Dependency or library version changes appear in build or manifest files (`build.gradle`, `../../../build.gradle.kts`, `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, etc.).
- A user asks to review library updates for breaking changes, behavioral changes, or deprecations.
- Upgrading dependencies requires recommended code updates, migration steps, and testing guidance.

## Workflow Steps

### 1. Identify Dependency Version Changes

- Inspect dependency declaration and lock files in the diff or changeset.
- Record the exact coordinates, `previous_version`, and `new_version` for each modified library.
- Disregard unchanged libraries to keep the analysis scoped and relevant.

### 2. Retrieve Release Notes and Changelogs

- Check official sources between `previous_version` and `new_version` (GitHub Releases, official release notes, migration guides, or commit logs).
- Extract relevant changes:
  - **Breaking changes**: Removed/renamed classes, functions, properties, or altered type signatures.
  - **Behavioral changes**: Modified default configurations, concurrency/lifecycle shifts, altered return semantics, or side-effects.
  - **Deprecations**: Methods or interfaces marked for removal that affect current usage.

### 3. Cross-Reference Against Project Code

- Search the project codebase for references to affected APIs or changed defaults.
- Categorize each dependency update into an impact level:
  - **None**: Internal/patch fixes; no APIs used in the codebase were changed or affected.
  - **Minor**: Non-breaking changes, deprecations with straightforward drop-in replacements, or optional opt-in flags.
  - **Breaking**: Incompatible compile-time or runtime behavior changes requiring code refactoring.

### 4. Produce Migration Recommendations and Diffs

For each affected code location:
- Specify the root cause of the required change.
- Provide a clean diff showing old vs. new implementation.
- Include testing and validation steps (unit tests, UI tests, or flags to verify).

## Output Format

Organize findings using the following structure:

### 1. Summary of Updates

| Dependency | Previous | Current | Impact Level |
| :--- | :--- | :--- | :--- |
| `group:artifact` | `x.y.z` | `a.b.c` | None / Minor / Breaking |

### 2. Changelog & Breaking Change Highlights

For each dependency with notable changes:
- **`[Library Name]` (`previous` -> `current`)**
  - **Release Notes / Changelog Source**: Link or reference to upstream releases.
  - **Key Behavioral & Breaking Changes**: Summary of relevant modifications.
  - **Affected Code Locations**: File paths and line references where affected symbols are used.

### 3. Recommended Code Updates

For each affected file or module:
- **Context**: Reason for the change.
- **Proposed Diff**:
  ```diff
  - oldUsage()
  + newUsage()
  ```
- **Verification Guidance**: Commands or test suites to execute to validate correctness.

## Pitfalls to Avoid

- Do not list full upstream changelogs for features the project does not consume. Filter strictly by relevance to the project repository.
- Avoid guessing version diffs without checking the actual manifest or lockfile state.
- Always differentiate between compile-time breakages and subtle runtime behavioral modifications.
