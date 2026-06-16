# ADR-020 Node Approval Indication

## Problem & Context
- REQUIREMENTS requires every graph node's representation to indicate its approval status, and the node context menu to list an "Approved" item carrying a trailing tick when approved.
- This is the graph-side sibling of ADR-019: the combined diff shows and toggles approval in the editor margin and content; the graph must show and toggle it on the node itself.
- Approval state, descendant-validation semantics, and persistence are owned by ADR-018; this record decides only how a graph node surfaces and toggles it.
- The graph is rendered by the Compose surface (ADR-009) from a render-facing projection of the domain graph (ADR-007). That surface must receive approval as derived state and emit toggles as callbacks; it must not read persistence or call domain mutation directly (ADR-001).
- Indication on the graph and in the diff must agree, since both derive from the same ADR-018 model.

## Constraints
1. Each graph node's representation MUST indicate its approval/validation status.
2. The node context menu MUST list an "Approved" item that shows a trailing tick when the node is validated, and activating it MUST toggle approval.
3. Indication MUST reflect ADR-018 **effective validation** — a leaf by its own entry, a container by all its changed leaves being validated — not only the node's own entry.
4. The rendering surface MUST consume approval as derived projection state and emit toggles as callbacks (ADR-001, ADR-009); it MUST NOT read persistence or mutate the ADR-018 model directly.
5. Graph indication MUST be consistent with the combined-diff indication (ADR-019) in the sense that both derive from the one ADR-018 model and never contradict it; it need NOT use the same visual vocabulary. In particular the graph MAY render a ternary full/partial/none marker where the diff renders a binary filled/unfilled green circle, because a collapsed graph node hides the per-leaf circles the diff always shows.

## Decision
- Effective validation MUST be carried into the render-facing graph view model as derived per-node state, computed from ADR-018 when the projection is built (ADR-007/ADR-009 projection, applied alongside ADR-008 collapse aggregation). For container nodes this MUST be the ADR-023 full/partial/none completeness derived over the node's changed leaves, not a single boolean; for leaves it is own-entry presence. The Compose surface MUST read this derived state and MUST NOT recompute it from persistence (constraints 3, 4).
- The node representation MUST carry a visual approval marker driven by that derived state. A leaf node MUST show a **green circle badge — filled when approved, unfilled (outline only) when not yet approved** — using fill rather than hue to distinguish the two states. A container node MUST show a **pie-chart badge** expressing the ADR-023 full/partial/none completeness over its changed leaves, since a collapsed node cannot lean on visible leaf markers the way the diff does (constraints 1, 5). The aggregate badge MUST be shown **always** — on both collapsed and expanded containers. Nodes with no changed leaves (nothing approvable, including external nodes) MUST carry **no marker** and need no approval state.
- The node context menu MUST include an item labeled simply **"Approved"** (identical text for leaves and containers) that renders a trailing tick when the node is effectively validated; the tick is **binary** — shown only when fully validated, absent for partial and none. Activating the item MUST invoke a node-scoped approval-toggle callback that follows ADR-018's ripple — toggling a leaf's own entry, or the rippled changed-leaf set for a container — mirroring ADR-019's toggle target (constraint 2).
- Toggle callbacks MUST flow through the ide layer to the ADR-018 model; after a toggle the projection MUST be re-derived so the marker, the context-menu tick, and the ADR-019 diff decorations all reflect the new state (constraints 4, 5). The whole projection MAY be re-derived rather than the affected subtree — no premature optimization until measured need.

## Rationale
- Computing effective validation once when building the projection and handing the surface a plain flag keeps the Compose layer free of git/PSI/persistence (ADR-001, ADR-009) and reuses the projection seam ADR-008 already defines, rather than teaching the renderer the ancestor-walk rule.
- Rippling the same way on toggle keeps the graph and diff on identical model semantics, so the two views can never disagree about what an action did.
- Deriving the tick and the node marker from the same flag makes the context-menu state and the node representation a single source of truth, satisfying the "indicate status" and "trailing tick" requirements without two code paths.

## Resolved Questions
1. **Leaf marker** — green circle badge: filled when approved, unfilled (outline) when not yet approved.
2. **Container marker** — pie-chart badge over the changed-leaf set conveying full/partial/none.
3. **Collapsed vs. expanded** — aggregate badge always shown, on both collapsed and expanded containers.
4. **Context-menu label** — just "Approved", identical for leaves and containers.
5. **Tick semantics** — binary; shown only when fully validated, absent for partial and none.
6. **Unchanged / non-approvable nodes** — no marker.
7. **External nodes** — do not need approval; carry no marker.
8. **Re-derivation granularity** — re-derive the whole projection; no premature optimization.
9. **Accessibility** — the leaf marker now distinguishes states by fill (filled vs. outline) rather than hue, so it is not color-dependent; no further non-color encoding added for now.

## Notes
- Visual marker is settled (see Resolved Questions): leaf = green circle badge (filled = approved, unfilled = pending), container = pie-chart badge. Leaf keeps ADR-019's green circle but encodes the unapproved state as an unfilled outline rather than a red fill; container extends it to a pie chart because a collapsed node hides the per-leaf circles.
- How a collapsed/aggregated node (ADR-008) indicates mixed descendant approval is resolved by the ADR-023 full/partial/none completeness state carried in the projection; this record consumes that state rather than a single boolean. The pie-chart badge renders that state.
- Under ADR-018's rippled model, every leaf under an approved container carries its own entry and can be un-approved individually from its context menu, dropping the container to partial validation. There is no revoke-via-ancestor indirection.
