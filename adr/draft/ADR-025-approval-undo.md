# ADR-025 Approval Undo

## Problem & Context
- Approval toggles are user mutations: ADR-019 toggles from the combined diff gutter, ADR-020 toggles from the graph node context menu, both mutating the one ADR-018 model.
- The graph canvas already exposes an Undo action (REQUIREMENTS, graph canvas actions). ADR-018 states that undo of an approval is the removal of a single entry, but does not say which undo stack a toggle participates in or whether it is undoable at all.
- A gutter or menu misclick toggling approval is easy to make, so reviewers will reasonably expect it to be reversible by the same Undo they already use.
- Approval mutations cross a layer boundary: they originate in the ide layer (diff dialog, graph surface) and must reach the domain ADR-018 model and its persistence (ADR-001). Where the reversible command lives determines whether undo survives, e.g., closing the diff dialog.

## Constraints
1. An approval toggle MUST be reversible.
2. A toggle from the combined diff (ADR-019) and a toggle from the graph (ADR-020) MUST be reversible through one shared history, since both mutate the single ADR-018 model.
3. Undo of an approval MUST restore the exact prior entry state (present/absent), consistent with ADR-018's "undo = remove/restore one entry."
4. The reversible command MUST flow through the domain mutation API across ADR-001 layers; the ide surfaces MUST NOT mutate persistence directly to implement undo.
5. Undo of an approval MUST re-persist the resulting state, so the persisted map (ADR-018) and in-memory state never diverge.

## Decision
- An approval toggle MUST be modeled as a single reversible command — add-entry or remove-entry against the ADR-018 model — recorded on the **same interaction-undo history** the graph canvas Undo already drives, alongside collapse/expand and other canvas mutations (constraints 1, 2).
- The command MUST own both directions: apply writes/removes the entry and re-persists; undo restores the prior entry state and re-persists (constraints 3, 5).
- The command and its history MUST live with the editor panel / orchestration boundary (ADR-001), NOT with the transient diff dialog, so that an approval toggled in the diff remains undoable after the dialog closes and shares history with graph-side toggles (constraints 2, 4).
- The ide surfaces (ADR-019 gutter action, ADR-020 menu item) MUST issue the toggle as this command through the domain mutation API; they MUST NOT reach into persistence to undo (constraint 4).
- Content-driven invalidation (ADR-022/ADR-023) and scope changes (ADR-021) are system events, NOT user commands; they MUST NOT be placed on the undo history and MUST NOT be undoable. Undo reverses user toggles only.

## Rationale
- One shared history is the only model consistent with one shared ADR-018 state: if diff-side and graph-side toggles had separate undo stacks, undo would diverge from the single source of truth and surprise the reviewer.
- Hosting the command at the editor/orchestration boundary rather than in the diff dialog is what lets undo outlive the dialog and stay unified with graph interactions, which is impossible if the history is owned by a transient window.
- Routing toggles through the domain mutation API keeps the ADR-001 layering intact and means undo reuses the same write-and-persist path as the forward action, so the two can never implement inconsistent persistence.
- Excluding system-driven invalidation from the undo history keeps undo meaning "take back what I did," not "resurrect an approval the content change legitimately revoked," which would let undo defeat the invalidation requirement.

## Notes
- Open for this draft: whether approval toggles share one interleaved history with collapse/expand or sit on a parallel approval-only history that the same Undo action drains. Shared interleaved history is taken as the default for a single, predictable Undo; a parallel history is the alternative if mixing structural and approval undo proves confusing.
- Open: redo semantics and history depth — assumed to follow whatever the existing graph canvas Undo already provides, not specified here.
- Depends on ADR-001, ADR-018; interacts with ADR-019, ADR-020; deliberately excludes ADR-021/022/023 system events from undo.
