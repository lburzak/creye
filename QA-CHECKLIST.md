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

## Approvals

### Automated Verification

- [ ] Run `rtk ./gradlew test`.
- [ ] Confirm the full suite passes.

### IDE Smoke Verification

- [ ] Open the dependency graph and select a comparison branch.
- [ ] Approve a changed leaf from the node context menu.
- [ ] Confirm the graph updates immediately without rerunning graph analysis.
- [ ] Confirm the approved leaf shows a filled green approval badge.
- [ ] Confirm the approved leaf context menu shows `Approved` with a trailing tick.
- [ ] Approve a container and confirm descendant changed leaves become approved.
- [ ] Confirm the approved container pie badge becomes full.
- [ ] Revoke one approved leaf under that container and confirm the container drops to partial.
- [ ] Reopen the graph and confirm approvals are restored from persisted project state.
- [ ] Change only one approved leaf, refresh analysis, and confirm only that leaf loses approval while unchanged sibling approvals remain approved.
- [ ] Open the combined diff and confirm no `Approve: X.kt` / `Approved: X.kt` buttons appear in the top header.
- [ ] Confirm file/class/symbol gutter circles appear at the larger marker size.
- [ ] Confirm filled gutter circles toggle to unfilled when revoked.
- [ ] Confirm unfilled gutter circles toggle to filled when approved.
- [ ] Confirm approved leaf ranges are highlighted.
- [ ] Confirm the current combined-diff scroll position is preserved across approval toggles.

### Approval Marker Vocabulary

- [ ] Confirm approved markers are filled green circles.
- [ ] Confirm unapproved markers are unfilled green outline circles.
- [ ] Confirm red is not used for unapproved approval state.
