# ADR-008 Collapsed Dependency Aggregation

## Problem & Context
- The campaign requires collapse and expand behavior for file, package, module, and class nodes.
- Collapsing a node hides descendants but must preserve visible evidence of their dependencies.
- Aggregated dependency edges can become misleading if direction, classification, or multiplicity is lost.
- The renderer needs a predictable visible graph derived from the full graph and current collapse state.
## Constraints
1. The implementation MUST preserve visibility of dependencies when nodes are collapsed by aggregating child edges onto visible ancestors.
2. Aggregation MUST preserve dependency direction.
3. Aggregation MUST preserve inbound, outbound, and internal classification where feasible.
4. Aggregation MUST avoid rendering duplicate equivalent visible edges.
5. Aggregation SHOULD retain traceability to the underlying hidden edges for diagnostics or drill-down.
## Decision
- TBD through targeted discussion.
## Rationale
- TBD through targeted discussion.
