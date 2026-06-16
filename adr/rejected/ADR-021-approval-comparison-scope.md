# ADR-021 Approval Comparison Scope

## Problem & Context
- Approval (ADR-018) is only meaningful relative to a diff. The combined diff compares the working directory against the merge-base of HEAD and the ADR-011 selected comparison branch, so the open question was whether an approval is bound to the comparison context it was made in.
- ADR-018 keys the persisted approval map by node path alone. It says nothing about the comparison context the approval was made in.
- The ADR-011 comparison branch can change between sessions, and HEAD or the merge-base can move under a fixed branch.
- The proposal feared that a node unchanged against branch A but changed against branch B, or carrying identical content under two different baselines, would let a path-only approval surface against the wrong baseline — and that scope partitioning was needed to prevent it.
- ADR-011 already persists the selected branch per project and surfaces a diagnostic when a persisted branch is absent from the repo; approval scoping was meant to compose with that.

## Constraints
1. A persisted approval MUST NOT apply to a comparison context other than the one it was made in.
2. Switching the ADR-011 comparison branch MUST present that branch's own approval set, and MUST NOT cross-contaminate sets between branches.
3. Movement of HEAD or the merge-base under a fixed comparison branch MUST be handled by content invalidation (ADR-022), NOT by discarding the whole scope.
4. Approval scoping MUST reuse the ADR-011 branch identity already persisted; it MUST NOT introduce a second comparison-configuration surface.
5. Approvals stored under a comparison context that is currently unavailable (e.g. an ADR-011 deleted branch) MUST remain stored and inert, never silently re-targeted to another context.

## Decision
- The persisted approval map would be partitioned by a **comparison-scope key** equal to the ADR-011 comparison branch identity, so each entry lived under `(scope key, node key, fingerprint)`.
- The scope key would be the branch the approval was made against, taken from the ADR-011 persisted selection.
- HEAD/merge-base movement under a fixed branch would be left to the ADR-022 fingerprint; only a branch switch would change the active partition.
- A partition whose branch is absent from the repo would be retained as inert storage, never migrated.

## Rationale
- The proposal rested on the premise that two baselines can yield byte-identical *after-side* content yet represent *different reviews*, so a fingerprint could not stand in for scope ("is this the same review context").
- Under the ADR-022 both-sides fingerprint the premise's first half holds — two baselines ARE two different reviews — but its conclusion fails: the fingerprint already distinguishes them, because the before-side moves with the baseline. Scope is therefore redundant, not necessary. See Rejection reason.

## Rejection reason
- **The verdict — no comparison-scope partition — holds, but the redundancy comes from the fingerprint capturing the baseline, not from the baseline being irrelevant.** Approval asserts that the *change* is acceptable (ADR-018), so ADR-022 fingerprints the diff a leaf owns on **both sides** — before and after. The before-side encodes the ADR-011 comparison baseline, so the baseline is *inside the fingerprint*, not outside it.
- **The baseline-sensitive fingerprint already does what scope partitioning was meant to do.** Switching the ADR-011 comparison branch changes the before-side, changes the diff, changes the fingerprint, and invalidates the entry — even when the working tree is byte-identical. This is exactly the cross-baseline distinction constraints 1–2 reached for, achieved by the fingerprint rather than a second comparison-configuration surface. Node key + both-sides fingerprint fully determine retention; a scope key adds nothing on top (this is what ADR-022 cites when it calls the partition redundant).
- **An earlier draft of this rejection rested on the opposite, now-superseded semantic** — an after-side-only fingerprint, "branch-independent," under which the baseline was deemed irrelevant and a branch switch on an unchanged working tree would *not* force re-review. That reasoning is withdrawn: ADR-022's fingerprint is both-sides and baseline-sensitive, so a branch switch on an unchanged tree *does* force re-review. The verdict survives the change of semantic; the reason inverts (baseline captured, not baseline ignored).
- **The remaining cases that might seem to demand scope are owned elsewhere.** Deletions (before-side content, empty after-side — approvable under ADR-022), reverts/round-trips, and responsibility-scoping belong to the fingerprint domain (ADR-022) and the model/missing-node records (ADR-018, ADR-024), not to branch-scope.

## Notes
- On rejection, dependent records drop their scope references: ADR-022 retention becomes node-key + fingerprint; ADR-024 migration is no longer scope-bounded; ADR-025 no longer lists scope changes as a system event.
- The work this record deferred to ADR-022 (per-entry invalidation) is unaffected; only the scope leg is removed from ADR-022's retention conjunction.
