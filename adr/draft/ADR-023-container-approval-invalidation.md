# ADR-023 Container Approval Invalidation

## Problem & Context
- ADR-018 lets the reviewer approve a container node (module, package, or file with declaration children) as a single entry, with descendants derived as validated by ancestor walk.
- The REQUIREMENTS invalidation rule keys on "that node's diffed content changes." A container has no content of its own — only its descendants do — so the rule has no direct meaning for a container entry until "a container's content" is defined.
- ADR-022 defines a fingerprint only for leaf (own-content) nodes and explicitly delegates aggregation here.
- This is where the positive-only ancestor-walk model and granular invalidation meet: if one descendant under an approved module changes, it must be decided whether the whole module approval goes stale or only the changed descendant's derived validation drops.
- ADR-020 left open how a collapsed/aggregated node (ADR-008) indicates approval when its descendants are in mixed states; that display question shares the same aggregation decision.

## Constraints
1. A container approval MUST be invalidated when the change set materialized under it changes — a descendant added, removed, or whose ADR-022 fingerprint changes.
2. Invalidating a container approval MUST NOT delete descendants' own approval entries; those MUST survive on their own ADR-022 terms.
3. Effective validation MUST remain the ADR-018 ancestor walk; this record adds only what invalidates a container entry, not a new validation path.
4. A collapsed/aggregated node (ADR-008) MUST be able to indicate full vs partial vs no validation of its subtree, derived in the projection (ADR-020).
5. Aggregation MUST reuse ADR-022 leaf fingerprints and the ADR-007 materialized neighborhood; it MUST NOT introduce a whole-subtree scan beyond what is materialized.

## Decision
- A container's fingerprint MUST be an order-independent aggregate over its **materialized changed descendants**, combining each descendant's ADR-005 node key with its ADR-022 fingerprint. Adding, removing, or changing any such descendant MUST change the aggregate and therefore invalidate the container's approval entry (constraint 1).
- Container approval invalidation MUST drop only the container's own entry. Descendants that carry their own entries MUST be evaluated independently against ADR-022 and retained or dropped on their own merits (constraint 2). After a container entry is dropped, descendants with no own entry revert to unapproved, because content under the container did change.
- Effective validation MUST stay the ADR-018 ancestor walk over surviving entries; a container entry simply stops contributing once invalidated (constraint 3).
- The render-facing projection MUST derive a per-container **validation completeness** state for collapsed/aggregated nodes (ADR-008): *fully validated* when the node is effectively validated, *partially validated* when some but not all materialized descendants are effectively validated with no covering container entry, and *not validated* otherwise. This resolves the ADR-020 open question (constraint 4).
- Aggregation MUST range only over the ADR-007 materialized neighborhood already present; it MUST NOT expand materialization to compute completeness (constraint 5).

## Rationale
- Defining a container's fingerprint as the aggregate of its descendants' fingerprints makes "approving a container" an honest statement about the change set under it as it stood: if any part of that set moves, the container-level sign-off is stale and must be re-confirmed, which is the safe default for review.
- Dropping only the container entry while leaving descendant entries intact preserves the granular model — a reviewer who also approved individual descendants keeps those — so coarse re-confirmation does not erase fine-grained work.
- Reverting uncovered descendants to unapproved on container invalidation is correct precisely because the trigger is a real content change somewhere under the container; pretending the rest is still reviewed would mask that.
- A three-state completeness projection gives the collapsed-node display enough to be honest (full/partial/none) without leaking the aggregation logic into the Compose surface, consistent with ADR-020's derived-state rule.

## Notes
- Tension acknowledged: approving a module and then changing one unrelated descendant invalidates the module approval wholesale. The alternative — invalidate only the changed descendant's contribution and keep the module "approved except X" — is rejected for this draft because it requires per-descendant exception state, which the positive-only model (ADR-018) deliberately excludes. Revisit only if reviewers find wholesale re-confirmation too costly.
- Open: the exact aggregate function (e.g. hash over a sorted list of `(key, fingerprint)` pairs) — left to implementation, constrained to be order-independent and to change on any member change.
- Depends on ADR-018, ADR-022; resolves an ADR-020 open note; interacts with ADR-008 collapse aggregation.
