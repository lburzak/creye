# ADR-025 Approval Reversal

## Problem & Context
- Approval toggles are user mutations: ADR-019 toggles from the combined diff gutter, ADR-020 toggles from the graph node context menu, both mutating the one ADR-018 model.
- A gutter or menu misclick toggling approval is easy to make, so a reviewer needs a way to take it back.
- The graph canvas already exposes an Undo action (REQUIREMENTS, graph canvas actions). ADR-018 mentions undo restoring the entry set an action wrote, which raises the question of whether approval toggles must join that undo history.
- Approval is a binary state per node. Unlike a structural edit, its inverse is itself: a second toggle reverses the first. This makes a dedicated undo path optional rather than necessary.

## Constraints
1. A mistaken approval toggle MUST be reversible by the reviewer.
2. Reversal MUST flow through the same domain mutation API as the forward toggle (ADR-001 layering); the ide surfaces MUST NOT mutate persistence directly.
3. Reversal MUST always act against the current ADR-018 state, never resurrect approvals that content-driven invalidation (ADR-022/023) has legitimately revoked.

## Decision
- Approval has **no dedicated undo**. The inverse of an approval toggle is the same toggle applied again: revoke an approval by toggling it off (ADR-020 menu, ADR-019 gutter), re-approve by toggling on (constraint 1).
- Reversal is therefore an ordinary forward mutation through the existing ADR-018 toggle API — the same write-and-persist path as the original action (constraint 2). There is no separate reversible command, no payload capture, no approval undo history.
- Because every revoke acts on the **current** model state, reversal composes cleanly with content-driven invalidation: a toggle never reaches back to a captured prior state and so can never resurrect an approval that ADR-022/023 already revoked (constraint 3).
- A container toggle stays the unit of action defined by ADR-020/ADR-018: approving a container ripples to its leaf set, revoking it ripples the inverse over the **current** leaf set. The reviewer reverses a container approval by revoking the container.
- Approval toggles MUST NOT be placed on the graph canvas interaction-undo history (collapse/expand, etc.). That history governs structural canvas edits only; approval reversal is self-served by re-toggling.

## Rationale
- Approval is binary, so its inverse is built in — a toggle is its own undo. A command/history layer would add machinery (payload capture, prior-state restore, shared-history coordination across diff and graph surfaces) for zero capability the toggle does not already provide.
- Routing reversal through the same toggle API keeps ADR-001 layering intact and guarantees forward and reverse can never implement inconsistent persistence, because they are the same operation.
- Acting only on current state is what keeps reversal from defeating invalidation. A restore-prior-state undo would have to choose between honoring its captured snapshot (resurrecting revoked approvals) and honoring invalidation (breaking its own contract); re-toggling has no such conflict.
- Keeping approval off the canvas undo history avoids a single Undo action mixing structural and approval semantics, which would make "what does Undo take back" ambiguous.

## Notes
- Known gap: a container approval over a leaf set where some leaves were already approved beforehand. Revoking the container revokes the whole current set, including those pre-existing approvals; the reviewer must re-toggle any leaf they meant to keep. Accepted as a rare, self-correctable case (the still-wanted approval is visible and one toggle away) — not worth a prior-state-restoring undo.
- Cross-surface reversal: an approval made in the diff gutter is revoked from the graph node (or the diff, while open). One extra navigation step, accepted.
- Supersedes the earlier draft direction of a reversible approval command on a shared undo history. Depends on ADR-001, ADR-018; interacts with ADR-019, ADR-020; defers to ADR-022/023 for system-driven revocation.
