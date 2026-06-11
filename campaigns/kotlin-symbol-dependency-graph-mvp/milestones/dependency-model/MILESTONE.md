# Milestone: dependency-model

Build the hierarchical node and directional dependency edge model: resolve what the changed code depends on and assemble the domain graph, per ADR-002 (project model), ADR-005 (node identity), ADR-006 (dependency resolution), ADR-007 (graph model), and ADR-010 (diagnostics).

## Definition of Ready

- ADR-002, ADR-005, ADR-006, ADR-007, ADR-010 MUST be accepted (they are).
- Milestone `change-detection` MUST be done (it is): `GraphAnalysisService.detectChanges(branch)` yields `ChangedSymbols` with ADR-005 identities.

## Definition of Done

- The domain graph model MUST follow ADR-007: structural nodes (ADR-005 path identity, containment derived from `NodePath` segment prefixes) separate from external nodes (semantic identity, outside the containment tree); directed edges carrying ADR-006 classification and dependency-kind metadata; changed state stored only as intrinsic structural-node state with the ADR-004 change kind; related and structural-scaffolding conditions derived, never stored.
- The dependency resolution stage MUST follow ADR-006: K2 Analysis API (`analyze { }`) only; references collected from changed line ranges of changed declarations, not whole files; edge kinds limited to function/constructor calls, property/field access, and type references; two-step classification (changed target → cohesion, else `ProjectFileIndex` source/library split → internal/external); `KaSymbol` bridged to ADR-005 identity (structural) or external semantic identity before leaving the session; unresolved references skip the edge and emit a diagnostic; deferred kinds (annotations, implicit receiver/invoke) emit diagnostics.
- Graph construction MUST materialize the minimal changed-code neighborhood per ADR-007: changed structural nodes, resolved internal/cohesion targets, and the full containment ancestor chain up to the module for every included structural node — no siblings or descendants. Module segments MUST use Gradle ids where available per ADR-002, with a `PROJECT_MODEL` diagnostic on fallback.
- Unresolved references MUST NOT create nodes or edges; they surface as diagnostics attached to the owning source structural node.
- `Diagnostic` MUST gain the optional ADR-007 attachment key (graph / node / edge) consumed per ADR-010; resolution and construction failures MUST surface under the `DEPENDENCY_RESOLUTION` and `GRAPH_CONSTRUCTION` sources respectively.
- Domain types MUST remain free of IntelliJ, Kotlin-PSI, Analysis-API, and git4idea imports (ADR-001 layering).
- `GraphAnalysisService.analyze(branch)` MUST return the real ADR-007 graph headlessly, replacing the placeholder `DependencyGraph`.
- `./gradlew test` and `./gradlew buildPlugin` MUST pass.

## Tasks

- `graph-domain-contracts` — domain value types (`domain/graph`, `Diagnostic.attachment`) per [contracts.md](contracts.md); replaces the placeholder `DependencyGraph`.
- `dependency-resolution` — ADR-006 stage: per-file `analyze { }` batching, reference collection within changed line ranges, edge-kind filtering, two-step classification, in-session identity bridging (reuse `NodePathFactory`/`ProjectFileSegments` from detection for source-declaration targets), unresolved/deferred diagnostics; fixture tests.
- `graph-construction` — ADR-007 assembly: node materialization from changed symbols and resolved edges, ancestor-chain completion via `NodePath` prefixes, edge dedup by value, diagnostic attachment; unit tests over pure inputs.
- `analysis-pipeline-wiring` — compose detection → resolution → construction in `GraphAnalysisService.analyze(branch)`: read-action and analysis threading discipline, diagnostic aggregation across all sources, cancellation pass-through; headless non-git-project test.

## Challenge

Idempotent verification of achievement:

- [ ] `./gradlew test` passes, run includes dependency-resolution and graph-construction test classes.
- [ ] Resolution tests cover: function call, constructor call, property access, type references (parameter, return, supertype, generic argument), cohesion edge between changed symbols in different files, internal target in another source file, external target in a library (e.g. stdlib), unresolved reference → diagnostic and no edge, deferred kind (annotation) → diagnostic.
- [ ] Construction tests cover: full ancestor chain up to module on both source and target sides, no sibling/descendant materialization, changed node carries ADR-004 change kind, external node outside the containment tree, duplicate edges deduplicated by value, diagnostics attached at graph, node, and edge level.
- [ ] `./gradlew buildPlugin` succeeds.
- [ ] `grep -rE "import (com\.intellij|org\.jetbrains\.kotlin|git4idea)" src/main/kotlin/pl/lukaszburzak/creye/domain/` returns nothing.
- [ ] Headless test passes: `analyze` on a non-git project yields a graph with no nodes or edges and a `git`-source error diagnostic.
- [ ] Manual (deferred to `mvp-validation`): `./gradlew runIde` end-to-end check once a rendering surface exists.
