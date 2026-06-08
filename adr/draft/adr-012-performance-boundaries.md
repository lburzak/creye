# ADR-012 Performance Boundaries

## Problem & Context
- Kotlin project analysis and dependency search can be expensive on large Gradle projects.
- The plugin must remain responsive while analysis runs.
- The MVP should prioritize changed files and changed symbols over exhaustive whole-project analysis.
- Partial, cancelled, or bounded analysis can still be useful if limitations are visible.
## Constraints
1. Long-running analysis MUST run outside the UI thread.
2. Long-running analysis MUST support cancellation.
3. The implementation SHOULD prioritize changed Kotlin files and changed symbols over whole-project exhaustive analysis.
4. The implementation MUST expose diagnostics when analysis is skipped, cancelled, truncated, or bounded.
5. The renderer MUST remain usable with partial graph results.
## Decision
- TBD through targeted discussion.
## Rationale
- TBD through targeted discussion.
