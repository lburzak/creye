# Kotlin Symbol Dependency Graph MVP

## Goal
- Realize the core use case defined in [CORE-USE-CASE.md](../../CORE-USE-CASE.md) as a shippable MVP.

## Scope
- The MVP MUST realize the full capability defined in [CORE-USE-CASE.md](../../CORE-USE-CASE.md); the bullets below state only how that capability is scoped down for the MVP.
- Branch selection is reduced to exactly one configured git branch compared against the current working directory.
- Changed-symbol detection and graph construction span the levels defined in the core use case (Gradle modules, packages, files, classes, symbols), at file, class, function, and property granularity where feasible.
- Dependency classification follows the internal / external / cohesion definitions in the core use case; no additional edge classes are introduced.

## Out of Scope
- The MVP MUST NOT require full semantic parity with the IntelliJ dependency analyzer.
- The MVP MUST NOT require perfect symbol resolution for generated sources, multiplatform source sets, or unresolved project states.
- The MVP MUST NOT require graph persistence across IDE restarts.
- The MVP MUST NOT require remote collaboration, export, or sharing features.
- The MVP MUST NOT require reverse usage search (which unchanged code depends on a changed symbol); only the dependencies the changed code itself declares are in scope.

## Milestones
- `plugin-foundation`: Establish the IntelliJ plugin shell and editor panel entry point. **Done**
- `change-detection`: Compare a branch against the working directory and locate changed Kotlin declarations.
- `dependency-model`: Build the hierarchical node and directional dependency edge model.
- `graph-rendering`: Render the collapsible dependency graph in an IntelliJ editor panel.
- `mvp-validation`: Validate the end-to-end flow on a representative Kotlin project.

## Completion
- The campaign is complete when every Milestone above is achieved; each Milestone's own Challenge defines its verification.
