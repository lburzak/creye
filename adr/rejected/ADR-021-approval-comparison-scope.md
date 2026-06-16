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

## Proposed Decision (rejected)
- The persisted approval map would be partitioned by a **comparison-scope key** equal to the ADR-011 comparison branch identity, so each entry lived under `(scope key, node key, fingerprint)`.
- The scope key would be the branch the approval was made against, taken from the ADR-011 persisted selection.
- HEAD/merge-base movement under a fixed branch would be left to the ADR-022 fingerprint; only a branch switch would change the active partition.
- A partition whose branch is absent from the repo would be retained as inert storage, never migrated.

## Rationale (of the rejected proposal)
- The proposal rested on the premise that two baselines can yield byte-identical node content yet represent *different reviews*, so the fingerprint ("did content change") could not stand in for scope ("is this the same review context").
- That premise is what this record ultimately rejects — see Rejection reason.

## Rejection reason
- **The founding premise is false under the chosen approval semantic.** Approval means "the new code has acceptable quality" — a judgment about the after-side content itself, not about the delta or the baseline it was diffed against.
- **After-side content is branch-independent.** The after-side is the working directory; the ADR-011 comparison branch only moves the baseline (before-side), changing *which* nodes show as changed and what the delta is — never the after-side text of a given node. So two baselines yielding byte-identical after-side content are reviewing the *same code*, which under the quality semantic is the *same review*, not different ones. The cross-contamination feared by constraints 1–2 cannot occur.
- **Scope partitioning would be not merely redundant but harmful.** Partitioning by branch would force re-review of byte-identical, already-approved code purely because the user re-pointed the diff — pure noise, the opposite of the goal.
- **ADR-022 already does the whole job.** Retention by node key + after-side fingerprint correctly retains an approval iff the approved code is unchanged, regardless of baseline. The scope leg added nothing a correct fingerprint did not already provide.
- **The cases that could justify a delta-review semantic are not solved by branch-scope.** Deletions (no after-side node to approve), reverts/round-trips (content fingerprint re-blesses an unseen A→B→A), and responsibility-scoping belong to the fingerprint domain (ADR-022) and the model/missing-node records (ADR-018, ADR-024). Branch-scope's only unique power — distinguishing two reviews with identical deltas — is demanded by none of them.

## Notes
- On rejection, dependent records drop their scope references: ADR-022 retention becomes node-key + fingerprint; ADR-024 migration is no longer scope-bounded; ADR-025 no longer lists scope changes as a system event.
- The work this record deferred to ADR-022 (per-entry invalidation) is unaffected; only the scope leg is removed from ADR-022's retention conjunction.
