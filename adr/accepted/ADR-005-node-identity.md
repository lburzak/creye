# ADR-005 Node Identity

## Problem & Context
- A node is any element of the graph hierarchy (module container, source set, package, file, class, or symbol). Graph construction, dependency resolution, collapse aggregation, rendering, and diagnostics all need stable references to the same nodes.
- Kotlin symbols can be overloaded, nested, moved, renamed, or unresolved.
- IntelliJ or Kotlin semantic identifiers may not be available for every declaration during analysis.
- Incorrect identity rules can merge unrelated symbols or split one logical symbol across graph phases.

## Constraints
1. Node identity MUST be deterministic within a single analysis run.
2. Node identity MUST distinguish node kinds: module containers, source sets, packages, files, classes, and symbols (functions, properties).
3. Node identity MUST tolerate unresolved semantic symbols.
4. Node identity MUST support nested declarations and overloaded callable declarations.
5. Node identity SHOULD include display metadata useful for diagnostics and rendering.
6. A node reached through structural discovery (ADR-002) and through symbol resolution (ADR-006) MUST yield the same identity. Resolution MUST derive identity from the symbol's source declaration, not a parallel semantic key. Symbols without a source declaration (external/library) are out of scope for this requirement.

## Decision
- Define node identity as a structural containment path: an ordered list of kind-tagged segments — module container, optional source set, package, file, then the declaration chain down to the symbol. Equality and hashing are by value over the full segment list, so equal paths are the same node.
- Derive every segment from PSI, reusing ADR-002 discovery. Identity MUST NOT use resolved semantic signatures, fully-qualified names, or resolved types.
- Module segment: a module *container* that parents all of a module's source sets. The container id is the Gradle module path with its source-set suffix removed where available (e.g. `creye:main` → container `creye`), else the IntelliJ module name (per ADR-002).
- Source-set segment: a hierarchy level between module and package, derived from the source-set suffix of the Gradle module id (e.g. `main`, `test`). A module container with no resolvable source set (the IntelliJ-module-name fallback) has no source-set segment. This makes a single module container parent its source sets rather than each source set appearing as a separate top-level module.
- Package segment: `KtFile.packageFqName`; use an explicit default-package sentinel when no package is declared.
- File segment: the file name together with its module-relative path, so same-named files in one module, source set, and package — different platform or directory locations — do not collide.
- Class and object segments: the containing declaration name chain, preserving nesting.
- Symbol segment (function, property): the declaration name plus, for callable declarations, an overload discriminator of arity, the ordered value-parameter type-reference text taken verbatim from `KtParameter`, and the extension-receiver type-reference text from `KtNamedFunction.receiverTypeReference`. Exclude the return type.
- Resolution (ADR-006) MUST mint identity by reducing the `KaSymbol` to its source PSI declaration and applying these same path rules. A symbol with no source declaration is external and is out of scope for hierarchy identity (constraint 6).
- Carry display metadata and semantic enrichment alongside identity, but exclude them from equality and hashing; the structural path is the sole equality key.
- The interned, in-memory identity handle is run-local: it MUST NOT be persisted, and interning a path to a handle is permitted only if it preserves path value-equality and does not derive from discovery order. A *serialization of the segment values* (the kind-tagged segment list as text) MAY be persisted and compared across runs, because it is derived from PSI text and is therefore stable for unchanged code; consumers MUST treat a segment-value serialization that no longer resolves to a node in a later run as a non-match, never as an error.

## Rationale
- A structural path is the only base that is always available: ADR-002 produces it even in partially indexed or unresolved states, satisfying constraint 3, while semantic keys can be absent and so cannot anchor identity.
- Keying solely on PSI text makes structural discovery and symbol resolution converge: ADR-006 reduces a `KaSymbol` to the same source PSI, so both phases read identical segment text and produce the same id, satisfying constraint 6 without a parallel semantic key.
- Kind-tagging segments distinguishes node kinds (constraint 2) and prevents a package and a class of the same name from colliding.
- The source set is a dedicated hierarchy level under a module container so a module's source sets group beneath one container node rather than appearing as separate top-level modules (REQUIREMENTS: Module container). The file segment still carries its module-relative path so same-named files across platform or directory locations within one source set do not collide and convergence is preserved.
- The overload discriminator works from PSI alone, so it holds when resolution is unavailable (constraint 3) and distinguishes overloads and extension receivers (constraint 4). Return type is excluded because Kotlin forbids overloading on it, so it adds no distinguishing power and only false-split surface. The residual false-split from differing type spellings (`Int` vs `kotlin.Int`, typealias vs target) is tolerable because identity is run-local and such spellings are rare within a changed-line window.
- Excluding all non-key fields from equality keeps identity from leaking into rendering or resolution, so two nodes are equal exactly when their structure matches.
- The in-memory handle stays run-local because the rejected caching ADR-014 means no analysis result is reused across runs; value-equality on the path makes determinism automatic, and interning is left available for ADR-007's edge-heavy storage, constrained to stay order-independent so it cannot reintroduce traversal-order dependence and break constraint 1. Persisting a *serialization* of the segments is a separate affordance for consumers that legitimately span runs (ADR-018 approval): the path is PSI-derived text, so it is stable while the code is unchanged and naturally stops matching when the code moves — which is the desired invalidation signal, not a defect.
