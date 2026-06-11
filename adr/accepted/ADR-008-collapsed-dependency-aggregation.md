# ADR-008 Collapsed Dependency Aggregation

## Problem & Context
- The graph must support collapse and expand behavior for file, package, module, and class nodes.
- Collapsing a node hides descendants but must preserve visible evidence of their dependencies.
- Aggregated dependency edges can become misleading if direction, classification, or multiplicity is lost.
- The renderer needs a predictable visible graph derived from the full graph and current collapse state.
## Constraints
1. The implementation MUST preserve visibility of dependencies when nodes are collapsed by aggregating child edges onto visible ancestors.
2. Aggregation MUST preserve dependency direction.
3. Aggregation MUST preserve internal, external, and cohesion classification where feasible.
4. Aggregation MUST avoid rendering duplicate equivalent visible edges.
5. Aggregation SHOULD retain traceability to the underlying hidden edges for diagnostics or drill-down.

## Decision
- Aggregation MUST be a pure projection from the ADR-007 domain graph and the current collapse state to the visible edge set, holding no state of its own (per ADR-007 and ADR-009).
- Aggregation MUST run in the rendering layer (ADR-001, ADR-009), recomputed from the domain graph each time the collapse state changes. Orchestration produces the domain graph once and MUST NOT re-run aggregation per collapse or expand interaction.
- Each hidden dependency edge MUST have both endpoints lifted to their nearest visible ancestor in the containment tree. The ADR-007 full ancestor chain materialization guarantees a visible ancestor exists on both source and target sides.
- When both lifted endpoints resolve to the same visible node, the edge MUST NOT enter the visible edge set. It MUST instead be recorded as intrinsic internal-dependency state on that collapsed node, rendered as a badge rather than a self-loop edge.
- Visible edges MUST be deduplicated by the key `(visible source, visible target, direction)`. Direction MUST be preserved, so opposing edges between the same pair remain distinct.
- A visible edge MUST carry the set of ADR-006 classifications present across its underlying hidden edges, not a single collapsed value. Classification MAY be omitted only for an underlying edge that genuinely lacks one.
- A visible edge MUST carry the set of underlying domain edge keys (ADR-007 canonical edge identity) for drill-down and collapsed-edge diagnostics.

## Rationale
- Modeling aggregation as a pure projection keeps the domain graph the single source of truth (constraint 1 satisfied without the renderer owning state), matching ADR-007 and ADR-009.
- Running the projection in the rendering layer is what makes collapse and expand interactive: collapse state lives at the render surface (ADR-009), so re-projecting there on each change avoids an orchestration round-trip, while the domain graph stays the immutable, once-produced source of truth (ADR-001).
- Lifting endpoints to the nearest visible ancestor is what preserves dependency visibility under collapse (constraint 1); ADR-007's guaranteed ancestor chains make it total.
- Recording same-node edges as a badge rather than a self-loop preserves internal-coupling evidence (constraint 1) while keeping direction (constraint 2) and dedup (constraint 4) meaningful, since neither applies to a self-loop.
- Carrying the classification set rather than a dominant value preserves internal/external/cohesion distinctions (constraint 3); the "where feasible" allowance is reserved for edges with no classification at all, not for merges across differing classes.
- The edge-key set satisfies constraint 5 and supplies the traceability ADR-007 already requires for collapsed-edge diagnostics; it also makes per-class counts derivable on demand, so they need not be stored.

## Notes
Rejected alternatives, kept so they are not relitigated:
- **Self-loop edge for same-node aggregation.** Rejected in favor of an intrinsic internal-dependency badge. A real self-loop edge produces edge-soup on every collapsed node, and direction (constraint 2) and dedup (constraint 4) are meaningless for it.
- **Dominant single classification on merged edges.** Rejected because it discards classification when underlying edges differ, which constraint 3 protects. The classification set is carried instead; the "where feasible" allowance covers only edges with no classification.
- **Storing per-class counts on visible edges.** Considered for renderer weighting, not adopted. The traceability edge-key set already permits deriving counts on demand, so storing them duplicates state.
