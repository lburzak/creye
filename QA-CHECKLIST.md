# QA Checklist

## Living Graph

### Automated Verification

- [ ] Run `rtk ./gradlew test`.
- [ ] Confirm the full suite passes.
- [ ] Confirm `LivingGraphSimulationTest` covers propagation, damping, drift configuration, drag release, and viewport containment.
- [ ] Confirm projection/view tests cover expansion, collapse, dependency aggregation, and hierarchy rendering.

### IDE Smoke Verification

- [ ] Open the dependency graph and confirm the graph renders hierarchy levels clearly.
- [ ] Double-click an expandable node and confirm it expands.
- [ ] Use the node context menu collapse action and confirm the node and sibling frontier collapse as expected.
- [ ] Confirm children are connected to the expanded node via edges.
- [ ] Confirm collapsed dependency aggregation is visible and does not render as misleading self-loops.
- [ ] Drag or disturb a node and confirm the graph remains responsive.
- [ ] Release a dragged node and confirm the layout continues smoothly from the released position.
- [ ] Zoom in and out and confirm graph interaction remains usable.
- [ ] Pause the living layout and confirm node motion stops.
- [ ] Resume the living layout and confirm node motion continues.
- [ ] Confirm nodes render without type-letter icons; shape alone indicates type (module diamond, source set hexagon, package square, class circle, symbol triangle).
- [ ] Confirm a module container parents its source sets (e.g. `main`/`test` appear under one module, not as separate top-level modules).
- [ ] Confirm external nodes are hidden by default and appear only after enabling "Show External nodes".
- [ ] Click a node and confirm the Combined Diff opens for that node and its descendants.
- [ ] Expand/pan/zoom, change a slider, switch to another editor tab and back, and confirm view state is preserved.
- [ ] Change the comparison branch and confirm view state resets.

## Approvals

### Automated Verification

- [ ] Run `rtk ./gradlew test`.
- [ ] Confirm the full suite passes.

### IDE Smoke Verification

- [ ] Open the dependency graph and select a comparison branch.
- [ ] Approve a changed leaf from the node context menu.
- [ ] Confirm the graph updates immediately without rerunning graph analysis.
- [ ] Confirm the approved leaf shows a bold circular solid green approval ring with clear padding around the node.
- [ ] Confirm the approved leaf context menu shows `Approved` with a trailing tick.
- [ ] Approve a container and confirm descendant changed leaves become approved.
- [ ] Confirm the approved container ring becomes thick and solid.
- [ ] Revoke one approved leaf under that container and confirm the container drops to a dashed or segmented partial ring.
- [ ] Reopen the graph and confirm approvals are restored from persisted project state.
- [ ] Change only one approved leaf, refresh analysis, and confirm only that leaf loses approval while unchanged sibling approvals remain approved.
- [ ] Open the combined diff and confirm no `Approve: X.kt` / `Approved: X.kt` buttons appear in the top header.
- [ ] Confirm file/class/symbol gutter circles appear at the larger marker size.
- [ ] Confirm filled gutter circles toggle to unfilled when revoked.
- [ ] Confirm unfilled gutter circles toggle to filled when approved.
- [ ] Confirm approved leaf ranges are highlighted.
- [ ] Confirm the current combined-diff scroll position is preserved across approval toggles.
- [ ] Place the caret inside a changed symbol in the combined diff and run "Toggle Approval of Symbol with Cursor" (Alt+Shift+A or Find Action); confirm that symbol's approval toggles.
- [ ] Confirm the action appears in Find Action and is disabled when the combined diff is not focused.
- [ ] Approve every changed symbol in a file, check "Collapse approved", and confirm that file collapses out of the combined diff; uncheck and confirm it returns.
- [ ] Approve a changed leaf, then edit its file and confirm the approval is invalidated without pressing Refresh.

### Approval Marker Vocabulary

- [ ] Confirm fully approved graph nodes use a bold circular solid green ring with clear padding around the node.
- [ ] Confirm partially approved graph containers use a thick dashed or segmented green ring.
- [ ] Confirm unapproved approvable graph nodes use a thick dotted green ring with the same line width as approved rings.
- [ ] Confirm approval rings remain legible when zoomed out.
- [ ] Confirm approval ring line width stays visually stable while zooming.
- [ ] Confirm red is not used for unapproved approval state.
