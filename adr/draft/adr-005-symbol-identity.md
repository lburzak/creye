# ADR-005 Symbol Identity

## Problem & Context
- Graph construction, dependency resolution, collapse aggregation, rendering, and diagnostics all need stable references to the same entities.
- Kotlin symbols can be overloaded, nested, moved, renamed, or unresolved.
- IntelliJ or Kotlin semantic identifiers may not be available for every declaration during MVP analysis.
- Incorrect identity rules can merge unrelated symbols or split one logical symbol across graph phases.
## Constraints
1. Symbol identity MUST be deterministic within a single analysis run.
2. Symbol identity MUST distinguish modules, packages, files, classes, functions, and properties.
3. Symbol identity MUST tolerate unresolved semantic symbols.
4. Symbol identity MUST support nested declarations and overloaded callable declarations.
5. Symbol identity SHOULD include display metadata useful for diagnostics and rendering.
