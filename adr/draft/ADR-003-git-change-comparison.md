# ADR-003 Git Change Comparison

## Problem & Context
- Analysis must compare one configured git branch against the current working directory.
- Git comparison produces file and hunk level data, while Kotlin symbol extraction needs source ranges and declarations.
- Git comparison logic must remain separated from Kotlin symbol extraction.
- Working directory state can include staged changes, unstaged changes, added files, deleted files, and renamed files.
## Constraints
1. The implementation MUST compare exactly one configured branch against the current working directory.
2. The implementation MUST expose git comparison output as file and source-range data only, keeping it free of Kotlin symbol extraction.
3. The implementation MUST include uncommitted working directory changes in the comparison.
4. The implementation MUST identify the changed Kotlin files before symbol extraction starts; this record owns the changed-file set that ADR-004 consumes.
5. The implementation MUST expose diagnostics for missing branches, non-git projects, unsupported file states, and diff failures.
