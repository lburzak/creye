# Living Graph Campaign

## Goal
- Deliver the Living Graph requirements from `REQUIREMENTS.md` while preserving the ADR-001 rendering boundary, ADR-008 projection model, ADR-009 Compose Canvas renderer, and ADR-013 ForceAtlas2 layout adapter role.

## Milestones
- `interaction-verification` — Verify expansion, collapse, dragging, zooming, dependency aggregation, hierarchy rendering, and pause behavior against focused tests and requirement checks. Challenge: `rtk ./gradlew test` MUST pass, and IDE smoke verification SHOULD confirm the graph remains responsive during drag, expand, collapse, zoom, pause, and resume.

## Completed Milestones
- `continuous-simulation` — Added the render-facing continuous force simulation in `LivingGraphSimulation`, wired it into `DependencyGraphView` and `GraphCanvas`, exposed Pause/Resume, and covered propagation, damping, drift configuration, drag release, and viewport containment in `LivingGraphSimulationTest`. Verified with `rtk ./gradlew test`.
