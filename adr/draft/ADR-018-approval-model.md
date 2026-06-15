# ADR-018 Approval Model

## Problem & Context
- A reviewer needs to mark parts of the change set as reviewed/approved. Approval is review-progress state laid over the changed-code graph (ADR-007) and the combined diff, not an analysis output.
- Approval applies to graph nodes at any containment level — module, package, file, class, symbol — so the model must address all ADR-005 structural node kinds, not just files.
- REQUIREMENTS requires approval status to be persisted and restored across sessions, and to be cleared whenever the diff changes.
- Approving a container (e.g. a module) should mean its descendants are validated, but the team wants this without rewriting descendant state: undo must stay trivial, and a later content change must invalidate only the part that changed, not the whole approval.
- The literal "clear approval whenever the diff changes" is coarse: re-running analysis after an unrelated edit would wipe approvals the reviewer already completed. The intent is to invalidate approvals whose underlying content changed, granularly.
- ADR-005 identity is deliberately run-local: it "MUST NOT persist or be compared across runs." Approval must persist and be matched against a future run's nodes, which is in direct tension with that rule and must be resolved here rather than by quietly persisting a `NodePath` handle.

## Constraints
1. Approval state MUST be representable for any ADR-007 structural node kind (module, package, file, class, symbol); external nodes (ADR-007) are out of scope.
2. Approval state MUST record the exact node the user acted on. Approving a container MUST NOT create or modify approval entries for its descendants.
3. A node MUST be derivable as validated when the node itself, or any of its containment ancestors, carries an approval entry. Validation of a node MUST NOT be stored, only derived.
4. Approval state MUST persist across sessions and MUST be restorable when the graph or combined diff reopens.
5. Approval persistence MUST NOT store the run-local ADR-005 `NodePath` handle; it MUST key on the persistable segment-value serialization ADR-005 permits for cross-run consumers.
6. An approval entry MUST be invalidated when its node's diffed content changes between runs, and MUST be retained when that content is unchanged.
7. Undo of a single approval action MUST be expressible as the removal of a single entry.

## Decision
- Approval state MUST be a map from a **persistable node key** to an approval record, holding exactly one entry per explicit user approval action. The map MUST live in the domain layer (ADR-001) and be exposed to the ide and rendering layers as state plus mutation callbacks; the rendering surface MUST NOT read or write the persistence store directly (ADR-001, ADR-011).
- The persistable node key MUST be a deterministic serialization of the ADR-005 structural-path segment **values** (the kind-tagged segment list down to the node), NOT the interned run-local handle. Matching a persisted approval to a live node within a run MUST be done by comparing this serialized form against the serialization of the freshly minted `NodePath`, never by persisting or restoring a handle (constraint 5).
- Approving a node MUST write a single entry for that node's key. Descendants MUST receive no entry (constraint 2). The **effective validation** of a node MUST be derived at read time by walking its containment ancestor chain: the node is validated iff it, or any ancestor, has an entry (constraint 3). This makes undo the removal of one entry (constraint 7) and lets a single descendant change invalidate only its own subtree relationship.
- Each approval entry MUST bind a **content fingerprint** of the approved node's diffed content captured at approval time (derived from the node's ADR-004 change kind and changed-content range). On every analysis run, an entry whose recomputed fingerprint differs from the stored one MUST be dropped, and an entry whose fingerprint matches MUST be kept (constraint 6). This fingerprint-scoped invalidation replaces the literal blanket "clear on every diff change": an unchanged node stays approved, a changed node loses approval.
- Approval state MUST be persisted in per-project plugin state (`PersistentStateComponent`), consistent with ADR-011's per-project persisted configuration, and restored on graph/diff open (constraint 4).
- Approval entries MUST only be minted for structural nodes (constraint 1); a request to approve an external node MUST be ignored, not persisted.

## Rationale
- Storing only the acted-on node and deriving descendant validation by ancestor walk is what makes both desired behaviors fall out for free: undo is one map removal, and "invalidate only what changed" needs no descendant bookkeeping because descendants were never written.
- Binding each entry to a content fingerprint turns the coarse "clear on diff change" requirement into granular invalidation: re-analysis after an unrelated edit keeps approvals whose nodes are byte-for-byte the same and drops only those whose content moved, which is the behavior the reviewer actually wants.
- Serializing ADR-005 segment values rather than persisting the handle keeps the in-memory `NodePath` handle run-local while still giving approval a stable cross-run key, because the segment text is deterministic for unchanged code. ADR-005 was amended to permit exactly this segment-value serialization for cross-run consumers, so the approval key rests on a sanctioned affordance rather than a contradiction.
- Per-project `PersistentStateComponent` reuses the persistence seam ADR-011 already established for the comparison branch, so approval does not introduce a parallel storage mechanism.

## Notes
- Open for this draft: the exact content-fingerprint definition (whether it hashes the node's changed-line set, the post-change source slice, or the ADR-004 change record), and how robust it must be to whitespace/formatting-only changes.
- The persistable key rests on ADR-005's amended rule, which now explicitly permits persisting and cross-run comparing a serialization of the segment values; only the in-memory handle stays run-local.
- An approval entry is the sole carrier of approval state: a node is either deliberately approved (own or ancestor entry) or carries no approval information. There are no negative or override entries, so a descendant cannot be individually un-approved while an ancestor remains approved — to revoke, the ancestor's entry is removed. The UI records (ADR-019, ADR-020) toggle only the clicked node's own entry against this positive-only model.
- This record owns the model and persistence only. How approval is triggered and shown is split across ADR-019 (combined diff) and ADR-020 (graph node), both of which derive effective validation from this model.
