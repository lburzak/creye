# ADR-006 Dependency Resolution

## Problem & Context
- The analysis focuses on the changed code; the insight is what symbols the changed code depends on.
- Dependency targets fall into three classes: unchanged symbols in the project (internal), symbols in libraries or the JDK (external), and other changed symbols (cohesion).
- Cohesion dependencies must include dependencies among changed symbols across different files.
- Dependencies must be resolved from the changed lines and declarations, not from whole changed files.
- The campaign does not require full semantic parity with IntelliJ dependency analyzer behavior.
- The campaign does not require reverse usage search (which unchanged code depends on a changed symbol); only the dependencies the changed code itself declares are in scope.
- Kotlin references may be unresolved because of generated sources, incomplete project state, indexing, or unsupported language constructs.
- IntelliJ ships the K2 Kotlin frontend by default; K1 resolution APIs are deprecated.
## Constraints
1. The implementation MUST prefer IntelliJ Platform and Kotlin symbol inspection APIs where available.
2. The implementation MUST classify each dependency edge of the changed code by its target as internal (an unchanged symbol in the project), external (a symbol in a library or the JDK), or cohesion (another changed symbol).
3. The implementation MUST include cohesion dependencies among changed symbols across different files.
4. The implementation MUST resolve dependencies from the changed lines and declarations, not from whole changed files.
5. The implementation MUST resolve dependencies by forward inspection of changed code only, and MUST NOT rely on reverse usage search.
6. The implementation MUST tolerate unresolved references without failing the whole analysis.
7. The implementation MUST expose diagnostics for unresolved references and unsupported dependency cases.
## Decision
- Resolve dependencies using the K2 Kotlin Analysis API (`analyze { }` / `KaSession`); do not use deprecated K1 descriptor resolution.
- Define edge direction explicitly: an edge `source -> target` means `source` declares a dependency on `target`. The source is always changed code; no edge whose source is outside the changed set is produced.
- Produce all edges in a single forward pass: for each changed declaration, collect the references located within its changed line ranges, resolve each reference to its target symbol within a `KaSession`, and classify the target.
- Classify each resolved target in two steps: if the target is a changed symbol, the edge is cohesion; otherwise determine internal vs external from the target's containing file via `ProjectFileIndex` (`isInSourceContent` → internal, `isInLibraryClasses` / `isInLibrary` → external).
- Treat the following reference kinds as dependency edges in the MVP: function and constructor calls, property and field access, type references (parameter, return, supertype, generic argument). Defer annotation references and implicit-receiver / implicit-invoke edges; record each deferred kind as a diagnostic.
- Bridge resolved `KaSymbol` results to the identity defined in ADR-005 before leaving the `KaSession`, because `KaSymbol` instances are valid only within their analysis session.
- Batch analysis per file to amortize `KaSession` setup, and run resolution under the required read-action and analysis-allowed threading rules.
- On an unresolved or error reference (`KaErrorCallInfo`, null symbol, or a target whose location cannot be determined), skip the edge, emit a diagnostic, and continue the analysis.
## Rationale
- The campaign only needs the dependencies changed code declares, so reverse usage search (`ReferencesSearch`) is dropped. This removes the expensive, index-fragile half of resolution and confines the whole analysis to local forward inspection of changed code.
- Restricting reference collection to changed line ranges keeps the graph about what actually changed, rather than every dependency of a file that happened to be touched (constraint 4).
- All three edge classes fall out of the same forward pass: cohesion when the target is itself changed, internal vs external from the target's `ProjectFileIndex` location, so cross-file cohesion (constraint 3) is captured without extra work.
- `ProjectFileIndex` is the reliable Platform API for source-vs-library origin, avoiding name- or package-based guessing for the internal/external split.
- K2 is the default frontend and K1 resolution is deprecated, so building on the Analysis API avoids committing the MVP to a sunset path.
- `KaSymbol` instances do not outlive their session, so resolution must hand edges to ADR-005 identity rather than retain raw symbols.
- Unresolved references are expected on generated sources, partially indexed projects, and unsupported constructs; treating them as diagnostics rather than failures satisfies constraints 6 and 7 while keeping the graph useful.
- The MVP edge-kind set covers the dependencies that dominate a representative Kotlin project; deferring annotations and implicit receivers limits scope while diagnostics keep the omission visible.
