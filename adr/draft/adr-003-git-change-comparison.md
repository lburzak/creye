# ADR-003 Git Change Comparison

## Problem & Context
- The MVP must compare one configured git branch against the current working directory.
- Git comparison produces file and hunk level data, while Kotlin symbol extraction needs source ranges and declarations.
- The campaign requires git comparison logic to remain separated from Kotlin symbol extraction.
- Working directory state can include staged changes, unstaged changes, added files, deleted files, and renamed files.
## Constraints
1. The implementation MUST compare exactly one configured branch against the current working directory for the MVP.
2. The implementation MUST keep git comparison logic separated from Kotlin symbol extraction.
3. The implementation MUST include uncommitted working directory changes in the comparison.
4. The implementation MUST identify changed Kotlin files before symbol extraction starts.
5. The implementation MUST expose diagnostics for missing branches, non-git projects, unsupported file states, and diff failures.
