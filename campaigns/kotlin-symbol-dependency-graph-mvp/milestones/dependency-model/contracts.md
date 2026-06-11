# Shared type contracts for dependency-model tasks

Every task implements against these signatures so tasks stay independent (PDR-001). Package root: `pl.lukaszburzak.creye.domain`. Domain types carry no IntelliJ/PSI/Analysis-API/git4idea imports. Existing `domain/change`, `domain/identity`, `domain/diagnostics` types are unchanged except where noted.

## `domain/graph` — ADR-007 model

```kotlin
enum class DependencyClassification { INTERNAL, EXTERNAL, COHESION }   // ADR-006
enum class DependencyKind { CALL, PROPERTY_ACCESS, TYPE_REFERENCE }    // ADR-006 edge-kind set; constructor calls fold into CALL

/** External semantic identity (ADR-007): ClassId/CallableId rendering, fqName fallback, captured in-session. */
data class ExternalSymbolId(val id: String, val displayName: String)

sealed interface GraphNodeId {
    data class Structural(val path: NodePath) : GraphNodeId
    data class External(val id: ExternalSymbolId) : GraphNodeId
}

/**
 * Containment is derived from NodePath segment prefixes — no parent pointers.
 * `change` is the only stored condition (ADR-007); related/scaffolding are derived.
 */
data class StructuralNode(
    val path: NodePath,
    val displayName: String,
    val change: ChangeKind?,        // null = not changed (ancestor or resolved target)
)

data class ExternalNode(val id: ExternalSymbolId)

/** Directed: source declares dependency on target. Value equality = canonical edge identity (ADR-007/008 key). */
data class DependencyEdge(
    val source: NodePath,           // always changed code, always structural
    val target: GraphNodeId,
    val classification: DependencyClassification,
    val kind: DependencyKind,
)

data class DependencyGraph(
    val structuralNodes: List<StructuralNode>,
    val externalNodes: List<ExternalNode>,
    val edges: List<DependencyEdge>,
    val diagnostics: List<Diagnostic>,   // full cross-cutting set, all sources (ADR-010)
)
```

Notes:
- Replaces the placeholder `domain/DependencyGraph.kt`; `GraphSurface`/editor stay compiling against the new type (render work is `graph-rendering`).
- Node lists are deduplicated by identity; edge list deduplicated by value.
- Materialized set = changed nodes + internal/cohesion targets + their full ancestor chains up to module (ADR-007). External targets get one `ExternalNode` each, no ancestors.
- A `FileMove` (rename without content change) contributes no nodes: per ADR-004 it is not a content change. Moved files enter the graph only through their changed declarations, if any.
- `ContextualDeclaration`s contribute no nodes either; contextual containment surfaces naturally when a changed member's ancestor chain materializes its containing class.

## `domain/diagnostics` — ADR-007 attachment (additive change)

```kotlin
sealed interface DiagnosticAttachment {
    data object Graph : DiagnosticAttachment                          // graph-level, no reliable element
    data class Node(val id: GraphNodeId) : DiagnosticAttachment
    data class Edge(val key: DependencyEdge) : DiagnosticAttachment   // only for existing edges
}

data class Diagnostic(
    val source: DiagnosticSource, val severity: Severity, val message: String,
    val location: SourceLocation? = null,
    val attachment: DiagnosticAttachment? = null,   // new, optional per ADR-010
)
```

Unresolved references: `source = DEPENDENCY_RESOLUTION`, attachment = `Node(Structural(owning declaration path))`, location = reference `file:line`, message carries reference text (ADR-007 evidence rule).

## Resolution stage output (orchestration-internal)

```kotlin
// orchestration/resolution
data class ResolvedDependencies(
    val edges: List<DependencyEdge>,
    val diagnostics: List<Diagnostic>,
)
// DependencyResolver.resolve(detection: ChangeDetection): ResolvedDependencies — runs inside read action + analyze {}
```

Resolution input scope: `detection.symbols.changed` declarations with `kind != DELETED` (deleted declarations have no current PSI to inspect; their absence is the change). Reference collection window: the declaration's own ranges intersected with current-side hunk ranges (ADR-006 constraint 4).

## Construction stage (pure)

```kotlin
// orchestration/graph
// GraphAssembler.assemble(symbols: ChangedSymbols, resolved: ResolvedDependencies,
//                         upstreamDiagnostics: List<Diagnostic>): DependencyGraph
```

Pure function over domain values — unit-testable without platform fixtures. Ancestor chains are computed by enumerating proper prefixes of every included structural `NodePath`.

## Service aggregate

```kotlin
// GraphAnalysisService.analyze(branch: String): Deferred<DependencyGraph>
// pipeline: detectChanges → DependencyResolver → GraphAssembler; diagnostics aggregated in graph order
```
