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
3. Indication MUST reflect ADR-018 **effective validation** (own or ancestor entry), not only the node's own entry.
4. The rendering surface MUST consume approval as derived projection state and emit toggles as callbacks (ADR-001, ADR-009); it MUST NOT read persistence or mutate the ADR-018 model directly.
5. Graph indication MUST be consistent with the combined-diff indication (ADR-019), as both derive from ADR-018.

## Decision
- Effective validation MUST be carried into the render-facing graph view model as a derived per-node flag, computed from ADR-018 when the projection is built (ADR-007/ADR-009 projection, applied alongside ADR-008 collapse aggregation). The Compose surface MUST read this flag and MUST NOT recompute it from persistence (constraints 3, 4).
- The node representation MUST carry a visual approval marker driven by that flag, using a vocabulary consistent with ADR-019's approved/unapproved distinction (constraints 1, 5).
- The node context menu MUST include an "Approved" toggle item that renders a trailing tick when the node is effectively validated; activating it MUST invoke a node-scoped approval-toggle callback that adds or removes the clicked node's **own** ADR-018 entry, mirroring ADR-019's toggle target (constraint 2).
- Toggle callbacks MUST flow through the ide layer to the ADR-018 model; after a toggle the projection MUST be re-derived so the marker, the context-menu tick, and the ADR-019 diff decorations all reflect the new state (constraints 4, 5).

## Rationale
- Computing effective validation once when building the projection and handing the surface a plain flag keeps the Compose layer free of git/PSI/persistence (ADR-001, ADR-009) and reuses the projection seam ADR-008 already defines, rather than teaching the renderer the ancestor-walk rule.
- Writing only the clicked node's own entry on toggle keeps the graph and diff on identical model semantics, so the two views can never disagree about what an action did.
- Deriving the tick and the node marker from the same flag makes the context-menu state and the node representation a single source of truth, satisfying the "indicate status" and "trailing tick" requirements without two code paths.

## Notes
- Open for this draft: the concrete visual marker on the node (badge, color, ring) and its alignment with ADR-019's green/red circle vocabulary; deferred to the interaction design shared with ADR-015/ADR-016/ADR-019.
- Open: how a collapsed/aggregated node (ADR-008) indicates approval when its descendants are mixed — e.g. all-validated vs partially-validated — which the per-node effective flag does not by itself express.
- Carried from ADR-018's positive-only model: a node validated only through an approved ancestor cannot be individually un-approved from its context menu. The toggle writes only its own entry and there are no negative entries, so revoking means removing the ancestor's entry. This is a closed decision, not a deferral.
