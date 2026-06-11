# ADR-003 Git Change Comparison

## Problem & Context
- Analysis must compare one configured git branch against the current working directory.
- Git comparison produces file and hunk level data, while changed symbol detection needs source ranges and declarations.
- Git comparison logic must remain separated from changed symbol detection.
- Working directory state can include staged changes, unstaged changes, added files, deleted files, and renamed files.
## Constraints
1. The implementation MUST compare exactly one configured branch against the current working directory.
2. The implementation MUST expose git comparison output as file and source-range data only, keeping it free of changed symbol detection.
3. The implementation MUST include uncommitted working directory changes in the comparison.
4. The implementation MUST identify the changed Kotlin files before changed symbol detection starts; this record owns the changed-file set that ADR-004 consumes.
5. The implementation MUST expose diagnostics for missing branches, non-git projects, unsupported file states, and diff failures.
## Decision
- Branch selection and upfront branch configuration validation are governed by ADR-011; git change comparison MUST consume the configured branch reference as input.
- The comparison baseline MUST be the merge base of `HEAD` and the configured comparison branch.
- The implementation MUST compare the baseline tree against the current working directory, including staged and unstaged changes.
- The implementation MUST normalize git output into changed-file records containing path, previous path when available, file state, Kotlin/non-Kotlin classification, baseline and current content access, and changed source ranges where available.
- Changed source ranges MUST be two-sided: each hunk MUST expose its baseline-side removed range and its current-side added range, so changed symbol detection can map additions and modifications against current content and deletions against baseline content.
- Modified Kotlin files MUST expose both baseline and current content and the two-sided changed ranges derived from diff hunks.
- Added Kotlin files MUST be represented as whole-file changes exposing current content.
- Deleted Kotlin files MUST be represented as file-level deletions exposing baseline content, without requiring current PSI extraction, so changed symbol detection can enumerate the deleted declarations.
- Renamed Kotlin files MUST preserve both previous and current paths; content changes after rename MUST still expose two-sided changed ranges and both baseline and current content when available.
- Git comparison MUST NOT perform Kotlin declaration lookup, changed symbol detection, dependency resolution, or graph construction.
- Non-Kotlin changed files MUST NOT be passed to ADR-004 as changed Kotlin files; they MAY be retained as comparison metadata or diagnostics.
- Comparison-time failures MUST be reported as git diagnostics, including missing or unresolved branch references, non-git projects, unsupported file states, and diff failures.

## Rationale
- Using the merge base avoids treating commits that exist only on the configured branch as local deletions, while still showing the user's current line of work relative to that branch.
- Comparing the baseline tree to the working directory includes committed, staged, and unstaged local changes in one analysis input, satisfying constraint 3 without requiring callers to run separate staged and unstaged comparisons.
- Normalizing git output before changed symbol detection gives ADR-004 a stable changed-file set and range contract, satisfying constraint 4 while keeping Kotlin PSI concerns out of git comparison.
- Representing added, deleted, and renamed files explicitly prevents downstream phases from inferring file state from path or range absence.
- Exposing two-sided ranges and both baseline and current content lets ADR-004 build current PSI for additions and modifications and baseline PSI for deletions, so deleted symbols can be enumerated without ADR-004 reaching into git, keeping git comparison separated from changed symbol detection (constraint 2).
- Keeping non-Kotlin changes out of ADR-004 preserves that record's Kotlin-symbol scope while still allowing diagnostics or future metadata consumers to explain ignored changes.
