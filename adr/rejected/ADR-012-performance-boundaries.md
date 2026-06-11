# ADR-012 Performance Boundaries

## Problem & Context
- Kotlin project analysis and dependency search can be expensive on large Gradle projects.
- The plugin must remain responsive while analysis runs.
- Analysis should prioritize changed files and changed symbols over exhaustive whole-project analysis.
- Partial, cancelled, or bounded analysis can still be useful if limitations are visible.
## Constraints
1. Long-running analysis MUST run outside the UI thread.
2. Long-running analysis MUST support cancellation.
3. The implementation SHOULD prioritize changed Kotlin files and changed symbols over whole-project exhaustive analysis.
4. The implementation MUST expose diagnostics when analysis is skipped, cancelled, truncated, or bounded.
5. The renderer MUST remain usable with partial graph results.

## Rejection reason
Explicit performance bounds are unnecessary at the current scope, and every constraint this record raises is already owned by a decided ADR:
- **Cost is bounded by design, not by budgets.** The expensive case — whole-project exhaustive analysis — is excluded at the source: ADR-003 scopes comparison to the changed-file set, ADR-004 maps hunks to changed declarations, ADR-006 resolves only those declarations' out-edges, and ADR-007 materializes only the changed-code neighborhood (changed nodes, resolved targets, ancestor chains). Graph size scales with the diff, not the repository. Adding node-count caps, time budgets, or depth limits to a graph that is already diff-sized solves a problem the design does not have (constraint 3 is satisfied by ADR-004/006/007).
- **Responsiveness and cancellation are already decided.** ADR-001 puts long-running analysis on a plugin-owned coroutine scope off the UI thread and makes it cancellable. Constraints 1–2 are restatements of that decision; re-deciding them here would relitigate a closed ADR.
- **Visibility of limited results is already decided.** ADR-010 owns the diagnostic type and a closed six-source taxonomy; a bound or cancellation hit during a stage surfaces under that stage's source, not a seventh "performance" source. ADR-007 already materializes partially and ADR-009 renders the view model as-is, so partial graphs are usable by construction. Constraints 4–5 add no new rule.
- **"Bounded" and "truncated" are dead vocabulary.** With no hard caps, analysis either completes or is cancelled (ADR-001). There is no truncation or budget state to diagnose, so the distinct "truncated/bounded" states this record names never occur.

This is rejected as premature rather than deferred-in-draft: there is no decision to make until real graphs prove large. Revisit as a fresh bounds/budget ADR only if real-world use shows changed-code graphs growing large enough that ADR-007 neighborhood scoping is insufficient on its own.
