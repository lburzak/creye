# ADR-005 Node Identity

## Problem & Context
- A node is any element of the graph hierarchy (module, package, file, class, or symbol). Graph construction, dependency resolution, collapse aggregation, rendering, and diagnostics all need stable references to the same nodes.
- Kotlin symbols can be overloaded, nested, moved, renamed, or unresolved.
- IntelliJ or Kotlin semantic identifiers may not be available for every declaration during MVP analysis.
- Incorrect identity rules can merge unrelated symbols or split one logical symbol across graph phases.
## Constraints
1. Node identity MUST be deterministic within a single analysis run.
2. Node identity MUST distinguish node kinds: modules, packages, files, classes, and symbols (functions, properties).
3. Node identity MUST tolerate unresolved semantic symbols.
4. Node identity MUST support nested declarations and overloaded callable declarations.
5. Node identity SHOULD include display metadata useful for diagnostics and rendering.
