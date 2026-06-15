# ADR-021 Approval Comparison Scope

## Problem & Context
- Approval (ADR-018) is only meaningful relative to a diff. The combined diff compares the working directory against the merge-base of HEAD and the ADR-011 selected comparison branch, so an approval means "I reviewed this node's change against that baseline."
- ADR-018 keys the persisted approval map by node path alone. It says nothing about the comparison context the approval was made in.
- The ADR-011 comparison branch can change between sessions, and HEAD or the merge-base can move under a fixed branch.
- A node can be unchanged against branch A but changed against branch B, or carry identical content under two different baselines while representing different reviews. A path-only key cannot distinguish these, so approvals made against one baseline can silently surface against another.
- ADR-011 already persists the selected branch per project and surfaces a diagnostic when a persisted branch is absent from the repo; approval scoping must compose with that, not duplicate or contradict it.

## Constraints
1. A persisted approval MUST NOT apply to a comparison context other than the one it was made in.
2. Switching the ADR-011 comparison branch MUST present that branch's own approval set, and MUST NOT cross-contaminate sets between branches.
3. Movement of HEAD or the merge-base under a fixed comparison branch MUST be handled by content invalidation (ADR-022), NOT by discarding the whole scope.
4. Approval scoping MUST reuse the ADR-011 branch identity already persisted; it MUST NOT introduce a second comparison-configuration surface.
5. Approvals stored under a comparison context that is currently unavailable (e.g. an ADR-011 deleted branch) MUST remain stored and inert, never silently re-targeted to another context.

## Decision
- The persisted approval map MUST be partitioned by a **comparison-scope key** equal to the ADR-011 comparison branch identity. Each entry therefore lives under `(scope key, node key, fingerprint)`, not under the node key alone (constraints 1, 2).
- The scope key MUST be the branch the approval was made against, taken from the same ADR-011 persisted selection that is the source of truth for the diff; no separate scope configuration is introduced (constraint 4).
- HEAD and merge-base movement under a fixed branch MUST NOT change or clear the scope. Whether an individual approval survives such movement is decided solely by the ADR-022 content fingerprint, so an unchanged node stays approved and a changed node invalidates regardless of why the diff recomputed (constraint 3).
- On comparison-branch switch, the active approval set MUST be the partition for the newly selected branch; the previous branch's partition MUST be retained untouched for when it is reselected (constraint 2).
- A partition whose branch is absent from the current repo MUST be retained as inert storage and MUST NOT be applied or migrated to another branch; this mirrors ADR-011's diagnostic-and-block on a missing persisted branch (constraint 5).

## Rationale
- Partitioning by branch rather than relying on the content fingerprint alone is necessary because two baselines can yield byte-identical node content yet represent different reviews; the fingerprint answers "did this content change," not "is this the same review context."
- Reusing the ADR-011 branch identity keeps approval scope welded to the one comparison surface the diff already reads, so the scope can never diverge from what the user is actually looking at, and no parallel setting is created.
- Delegating HEAD/merge-base movement to the fingerprint keeps scope coarse and stable while invalidation stays granular, so a routine `git pull` does not nuke a whole branch's approvals — only the nodes whose diffed content actually moved.

## Notes
- Open for this draft: whether the scope key should also fold in the merge-base commit (making approvals strictly baseline-pinned) or stay at branch granularity and lean entirely on ADR-022. Branch granularity is taken here as the default; baseline-pinning is the stricter alternative if reviewers report stale approvals after rebases.
- Open: retention policy for partitions of branches that no longer exist — inert forever, or pruned after some signal. Left to a persistence-hygiene decision rather than this record.
- Depends on ADR-018 (the map this partitions) and ADR-011 (the branch identity). Feeds ADR-022, which owns the per-entry invalidation this record deliberately defers to.
