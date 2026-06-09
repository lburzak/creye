# ADR-004 Changed Symbol Detection

## Problem & Context
- Analysis must identify changed Kotlin symbols at file, class, function, and property granularity where feasible.
- Git diffs identify changed lines or hunks, while Kotlin PSI identifies declarations and source ranges.
- Some edits affect a declaration directly, while others affect file-level structure, imports, annotations, modifiers, or containing declarations.
- Declaration mapping may be ambiguous for malformed code, incomplete indexing, or unresolved project state.
## Constraints
1. The implementation MUST operate on the changed Kotlin files identified per ADR-003, mapping each to declarations rather than re-deriving the changed-file set.
2. The implementation MUST identify changed class, function, and property declarations when feasible.
3. The implementation MUST preserve file-level change information when declaration-level mapping is ambiguous.
4. The implementation MUST distinguish changed declarations from surrounding contextual declarations (the containing or adjacent declarations a hunk touches without changing) where feasible.
5. The implementation MUST expose diagnostics for changed hunks that cannot be mapped to declarations.
