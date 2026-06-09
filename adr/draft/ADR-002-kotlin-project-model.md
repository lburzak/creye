# ADR-002 Kotlin Project Model

## Problem & Context
- The graph hierarchy must span Gradle modules, packages, files, classes, and symbols.
- This record must decide how ownership and hierarchy are discovered from an IntelliJ Kotlin project; it does not define node identity or the final graph model.
- Kotlin files may belong to different IntelliJ modules, Gradle source sets, and package declarations.
- The project model needs enough structural accuracy to make the graph understandable on a representative Kotlin Gradle project.
- Perfect handling of generated sources, multiplatform source sets, or unresolved project states is not required.
## Constraints
1. The implementation MUST prefer IntelliJ Platform APIs for project structure.
2. The implementation MUST prefer Kotlin PSI or Kotlin symbol inspection APIs for Kotlin declarations.
3. The implementation MUST represent module, package, file, class, function, and property hierarchy where available.
4. The implementation MUST tolerate unresolved or partially indexed project states.
5. The implementation MUST expose diagnostics when ownership of a file or symbol cannot be determined.
## Decision
- Build the project hierarchy from IntelliJ's project model first: for each Kotlin file in the analysis scope, use the owning IntelliJ module and its Gradle metadata where available to produce the module-level node.
- Treat the Gradle module as the canonical module level when Gradle metadata is available; otherwise fall back to the IntelliJ module name and emit a diagnostic that Gradle ownership was not available.
- Do not add source sets as a separate graph level. Preserve source set information as module or file metadata when IntelliJ exposes it, so `main`, `test`, or platform-specific ownership can still be shown in diagnostics and labels.
- Derive package nodes from the Kotlin file package directive (`KtFile.packageFqName`), not from directory layout. Use an explicit default-package node when no package is declared.
- Place each Kotlin file node under its owning package node and module node. If file ownership cannot be determined, place the file under an unresolved ownership node and emit a diagnostic.
- Discover class, function, and property hierarchy from Kotlin PSI declarations. Top-level declarations belong to the file; member declarations belong to their containing class or object; nested classes preserve their containing declaration chain.
- Use Kotlin symbol inspection only as enrichment for semantic metadata needed by downstream phases; unresolved semantic symbols MUST NOT prevent the structural PSI hierarchy from being produced.
- Bridge discovered declarations to ADR-005 node identity before leaving project-model construction, so downstream graph construction and dependency resolution refer to the same nodes.
- Ignore non-Kotlin files for hierarchy construction in this record, except when they are needed as diagnostics for unsupported changed files.
## Rationale
- IntelliJ's project model is the reliable source for file-to-module ownership in an imported Gradle project, satisfying constraint 1 without guessing from paths.
- Gradle module ownership is the hierarchy required by the core use case; keeping source sets as metadata preserves useful context without introducing an extra graph level that the graph hierarchy does not require.
- Kotlin package directives are authoritative for package ownership, while directory layout can drift or be non-standard, so package nodes are derived from `KtFile` instead of paths.
- PSI declaration traversal works in partially indexed or unresolved project states, which satisfies constraint 4 and keeps hierarchy construction independent from full dependency resolution.
- Using symbol inspection only for enrichment keeps ADR-002 focused on project structure while leaving semantic dependency resolution to ADR-006.
- Explicit unresolved ownership nodes and diagnostics make missing module, package, or declaration ownership visible instead of silently dropping code, satisfying constraint 5.
- Handing declarations to ADR-005 identity at the boundary prevents later phases from inventing separate identities for the same module, file, class, function, or property.
