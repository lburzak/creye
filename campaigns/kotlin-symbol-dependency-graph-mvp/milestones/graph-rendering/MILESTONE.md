# Milestone: graph-rendering

Render the collapsible dependency graph in the IntelliJ editor panel: project the domain graph through collapse state, draw it on a Compose canvas, and wire branch selection and analysis into the panel, per ADR-008 (collapse aggregation), ADR-009 (graph rendering), ADR-010 (diagnostics), and ADR-011 (configuration).

## Definition of Ready

- ADR-008, ADR-009, ADR-010, ADR-011 MUST be accepted (they are).
- Milestone `dependency-model` MUST be done (it is): `GraphAnalysisService.analyze(branch)` returns the real ADR-007 graph headlessly.
- The editor panel entry point MUST exist (it does): `DependencyGraphFileEditor` hosts `GraphSurface` through `JewelComposePanel`.

## Definition of Done

- Collapse aggregation MUST follow ADR-008: a pure projection from the ADR-007 domain graph and collapse state to the visible node and edge set, holding no state of its own; hidden edge endpoints lifted to the nearest visible ancestor; same-node lifts recorded as an intrinsic internal-dependency badge, never a self-loop; visible edges deduplicated by `(visible source, visible target, direction)` with direction preserved; each visible edge carrying the classification set and the underlying domain edge keys.
- The projection MUST run in the rendering layer, recomputed by recomposition when collapse state changes; orchestration MUST NOT be re-invoked on collapse or expand.
- The renderer MUST follow ADR-009: the domain graph and collapse state held as Compose state; the visible graph drawn in a Compose `Canvas`; collapse, expand, and selection handled as state transitions; no renderer-owned copy of git, PSI, or analysis data; Compose/Skiko/Jewel consumed from the platform-bundled runtime only.
- The rendered graph MUST distinguish changed nodes (ADR-004 change kind), related nodes, external nodes, and the internal / external / cohesion edge classifications visually.
- Diagnostics MUST be exposed in the panel per ADR-010 in a way that supports validation — at minimum, graph-level diagnostics visible without drill-down.
- Branch selection MUST follow ADR-011: a single-select Jewel dropdown in the panel header populated from the repository branch list; the selection is the sole source of truth; the last selection persisted in per-project plugin state and restored on open; analysis blocked while nothing is selected; a persisted branch missing from the branch list surfaces a diagnostic and stays unselected.
- The panel MUST invoke `GraphAnalysisService.analyze(branch)` on branch selection (and on explicit refresh), showing in-progress and failure states rather than a stale or empty surface.
- The rendering layer MUST stay a leaf per ADR-001: no imports of git4idea, PSI, or Analysis API; it consumes the domain graph only.
- `./gradlew test` and `./gradlew buildPlugin` MUST pass.

## Tasks

- `collapse-projection` — ADR-008 pure projection types and function in the rendering layer: endpoint lifting via `NodePath` containment, badge for same-node lifts, direction-preserving dedup, classification and edge-key sets; exhaustive unit tests over pure inputs.
- `graph-layout` — deterministic layout of the visible projection (hierarchical containment placement plus edge routing good enough for the MVP); pure function from visible graph to positions; unit-testable invariants (no overlapping sibling nodes, children within parent bounds).
- `graph-canvas` — Compose `Canvas` surface: draw nodes, containment, badges, and classified edges; hit-testing; selection; collapse/expand toggles driving the projection by recomposition; visual states for change kinds, related, external, and diagnostics.
- `branch-selection` — ADR-011 header: Jewel dropdown over the repository branch list, per-project persisted selection with restore, missing-branch diagnostic, analysis gating.
- `panel-wiring` — connect `GraphSurface` to `GraphAnalysisService`: trigger analysis on selection/refresh, cancellation on reselect, loading and error presentation, graph-level diagnostics display.

## Challenge

Idempotent verification of achievement:

- [ ] `./gradlew test` passes; run includes collapse-projection and layout test classes.
- [ ] Projection tests cover: endpoint lifting to nearest visible ancestor on both sides, same-node lift becomes badge not edge, dedup keeps opposing directions distinct, merged edge carries the full classification set, edge-key set references all underlying hidden edges, fully-expanded state reproduces the domain edges unchanged.
- [ ] `./gradlew buildPlugin` succeeds.
- [ ] `grep -rE "import (git4idea|org\.jetbrains\.kotlin\.(psi|analysis))" src/main/kotlin/pl/lukaszburzak/creye/rendering/` returns nothing.
- [ ] No Compose/Skiko/Jewel artifacts bundled: plugin distribution contains no `skiko`/`compose`/`jewel` jars of its own.
- [ ] Manual (`./gradlew runIde`): open the panel, select a branch, graph renders with changed/related/external distinction; collapse a module — child edges aggregate onto it with classification preserved; expand restores them; deselect/missing persisted branch blocks analysis with a diagnostic.
- [ ] Manual: restart the IDE — last branch selection restored in the dropdown before analysis runs.
