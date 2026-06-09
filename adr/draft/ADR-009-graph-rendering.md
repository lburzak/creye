# ADR-009 Graph Rendering

## Problem & Context
- The MVP must render the dependency graph inside an IntelliJ plugin editor panel.
- The renderer must display hierarchy, dependency edges, changed symbols, related nodes (as defined in CORE-USE-CASE.md), and unresolved or diagnostic states.
- Rendering technology affects interaction quality, dependency footprint, IntelliJ compatibility, and implementation effort.
- The graph must support collapse, expand, selection, and inspection during MVP validation.
## Constraints
1. The renderer MUST run inside an IntelliJ plugin editor panel.
2. The renderer MUST consume the graph view model (the render-facing projection of ADR-007's graph model produced by orchestration) instead of raw git, PSI, or dependency analysis data.
3. The renderer MUST support collapse and expand interactions for hierarchical nodes.
4. The renderer MUST preserve visibility of aggregated dependency edges.
5. The renderer SHOULD expose diagnostics in a way that supports MVP validation.
