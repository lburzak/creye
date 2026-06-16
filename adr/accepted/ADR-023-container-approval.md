# ADR-023 Container Approval

## Problem & Context
- ADR-018 ripples a container approval down to an entry per changed leaf in its subtree; a container carries no entry of its own. A container's validation is derived: it is validated iff every changed leaf under it is validated.
- The REQUIREMENTS invalidation rule keys on "that node's diffed content changes." A container has no content of its own — only its leaves do — so invalidation has no direct meaning at the container level; it is entirely a consequence of its leaf entries being dropped (ADR-022) or new changed leaves appearing.
- ADR-022 defines the fingerprint per leaf entry. With ADR-018 storing only leaf entries, there is no container entry to fingerprint, so container "invalidation" is no longer an aggregate-fingerprint comparison — it is a derivation over surviving leaf entries.
- This is the record where granular invalidation lands: a change to one leaf under an approved module drops only that leaf's entry, so the module is no longer *fully* validated but its untouched leaves stay approved. The wholesale module re-confirmation of the earlier single-entry model is gone.
- ADR-020 left open how a collapsed/aggregated node (ADR-008) indicates approval when its descendants are in mixed states; that display question is resolved here as the full/partial/none completeness derivation.

## Constraints
1. A container's full validation MUST cease when the change set materialized under it changes — a changed leaf added, removed, or whose ADR-022 fingerprint changes — because such a leaf is then unvalidated.
2. A change under a container MUST drop only the affected leaf's entry; every other leaf's entry MUST survive on its own ADR-022 terms. There is no container entry to drop.
3. Container validation MUST be derived per ADR-018 (validated iff all changed leaves validated); this record adds only the completeness derivation, not a new storage path.
4. A collapsed/aggregated node (ADR-008) MUST be able to indicate full vs partial vs no validation of its subtree, derived in the projection (ADR-020).
5. Derivation MUST reuse ADR-022 leaf entries and the ADR-007 materialized neighborhood; it MUST NOT introduce a whole-subtree scan beyond what is materialized.
6. A container with no materialized changed leaf descendant MUST carry no completeness state at all — neither *fully*, *partially*, nor *not* validated — because there is nothing to validate; the three-state derivation is defined only over a non-empty changed-leaf set.

## Decision
- A container MUST hold no approval entry and no aggregate fingerprint; its validation MUST be derived over its **materialized changed leaf descendants**, each evaluated by its own ADR-022 entry. A leaf whose entry was dropped (changed content), a removed leaf, or a newly added changed leaf with no entry each makes the container less than fully validated (constraint 1).
- A content change MUST drop only the affected leaf's ADR-022 entry; all other leaf entries survive on their own merits (constraint 2). Because containers were never written, there is no container entry to invalidate — the container's reduced validation is purely a consequence of its leaves.
- Container validation MUST be the ADR-018 downward derivation over surviving leaf entries (validated iff all changed leaves validated); this record adds no new entry or validation path (constraint 3).
- The render-facing projection MUST derive a per-container **validation completeness** state for collapsed/aggregated nodes (ADR-008): *fully validated* when every materialized changed leaf is validated, *partially validated* when some but not all are, and *not validated* when at least one changed leaf is present and none are validated. This resolves the ADR-020 open question (constraint 4).
- The completeness derivation MUST guard on a non-empty changed-leaf set: a container with zero materialized changed leaves emits **no** completeness state and the projection renders no approval badge for it — vacuous "all validated" MUST NOT surface as *fully validated*, which would falsely assert a review that never happened. *Not validated* is reserved for the ≥1-leaf, none-validated case and MUST NOT be conflated with the empty case (constraint 6).
- Derivation MUST range only over the ADR-007 materialized neighborhood already present; it MUST NOT expand materialization to compute completeness (constraint 5). This is sound because ADR-007 materializes **every** changed structural node as a primary neighborhood member (not via its ancestor), so the materialized changed-leaf set under a container is already its complete changed-leaf set. Only unchanged siblings/descendants go unmaterialized, and those never count toward completeness. There is therefore no changed leaf hiding outside the neighborhood that could leave a container falsely *fully validated*.

## Rationale
- Deriving container validation from leaf entries rather than an aggregate fingerprint is what delivers granular invalidation: a change to one leaf drops one entry, so the container falls to *partial* while its untouched leaves stay approved — instead of the whole container sign-off going stale. This is the behavior REQUIREMENTS now asks for and the reason ADR-018 ripples to leaves.
- Because there is no container entry, there is nothing to "re-confirm wholesale" and no aggregate to maintain; the cost moves to approval time (rippling N leaf entries) and reversal (ADR-025 revokes by re-toggling the container), which ADR-018 accepted.
- A newly added changed leaf under an approved container correctly leaves the container *partial* with no action: it has no entry, so the all-leaves-validated derivation simply fails until it is reviewed. No stale container entry can assert the new code was approved.
- A three-state completeness projection gives the collapsed-node display enough to be honest (full/partial/none) without leaking the derivation into the Compose surface, consistent with ADR-020's derived-state rule.

## Notes
- Reversal recorded: an earlier draft kept the module "approved except X" out of scope, invalidating module approval wholesale to avoid per-descendant exception state. REQUIREMENTS now mandates granular invalidation, so ADR-018 stores per-leaf entries and this record derives "approved except X" directly — exactly the behavior the earlier draft rejected. The accepted cost is per-leaf storage and per-leaf reversal (ADR-025).
- No aggregate function is needed any more: container completeness is a membership check over materialized changed leaves' ADR-022 entries, not a hash over `(key, fingerprint)` pairs.
- Depends on ADR-018, ADR-022; resolves an ADR-020 open note; interacts with ADR-008 collapse aggregation.
