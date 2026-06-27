# ADR-026 Graph Scoping

## Problem & Context
- A changed-symbol graph can be large; reviewers need to isolate a single node and what it depends on without losing their current layout and expansion state.
- The isolation trigger is an IntelliJ action (REQUIREMENTS: IntelliJ Actions — Scope to selected node), which runs outside the Compose composition, yet it must drive the rendered graph and be reflected in the graph control row.
- The "Show External nodes" toggle is a render-time projection input owned by the composition; scoping is a coarser, externally-driven filter and conflating the two would entangle action-driven state with composition-owned toggles.

## Constraints
1. Scoping MUST be a filter, not a layout reset — the expansion frontier, selection, and node positions MUST survive entering and clearing a scope.
2. The active scope MUST be observable by the rendering surface so an IDE action can set it and the control row can show and clear it.

## Decision
- A scope is a single structural node identity (`NodePath`); the scoped graph is that node, everything it contains, and the transitive dependency closure reachable from it, with the structural ancestor chains of every kept node retained so projection (ADR-008) can still lift edges onto visible ancestors.
- Scoping MUST be a pure domain transform from `DependencyGraph` to a filtered `DependencyGraph` (`scopedTo`), applied before projection so collapse aggregation and lifting are unchanged.
- The active scope MUST be held by the project-scoped controller as observable state, separate from the composition-owned view state, because it is driven by IDE actions; the rendering surface observes it and renders an isolation indicator with a clear control in the graph control row.
- The scope MUST be cleared when analysis re-runs against a different branch, consistent with the rest of view-state reset.

## Rationale
- Modeling scope as a pre-projection domain filter keeps all collapse/lift behavior (ADR-008) intact and makes the filter unit-testable without the rendering layer.
- Holding scope on the controller mirrors the existing combined-diff request channel, so an action outside Compose can drive the surface through an already-established reactive boundary (ADR-001) rather than reaching into composition-local `remember` state.
- Keeping the expansion frontier untouched satisfies the isolation intent: the reviewer narrows what is shown without rebuilding their drill-down.
