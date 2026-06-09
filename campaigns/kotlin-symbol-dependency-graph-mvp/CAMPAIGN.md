# Kotlin Symbol Dependency Graph MVP

## Goal
- Realize the core use case defined in [CORE-USE-CASE.md](../../CORE-USE-CASE.md) as a shippable MVP.

## Scope
- The MVP MUST compare one configured git branch against the current working directory.
- The MVP MUST identify changed Kotlin symbols at file, class, function, and property granularity where feasible.
- The MVP MUST resolve the changed code's dependencies from the changed lines and declarations, not from whole changed files.
- The MVP MUST construct a hierarchical graph spanning Gradle modules, packages, files, classes, and symbols.
- The MVP MUST support collapse and expand behavior that aggregates child dependencies onto collapsed parent nodes.
- The MVP MUST classify each dependency edge of the changed code by its target as internal (an unchanged symbol in the project), external (a symbol in a library or the JDK), or cohesion (another changed symbol).
- The MVP MUST render the graph inside an IntelliJ plugin editor panel.

## Out of Scope
- The MVP MUST NOT require full semantic parity with the IntelliJ dependency analyzer.
- The MVP MUST NOT require perfect symbol resolution for generated sources, multiplatform source sets, or unresolved project states.
- The MVP MUST NOT require graph persistence across IDE restarts.
- The MVP MUST NOT require remote collaboration, export, or sharing features.
- The MVP MUST NOT require reverse usage search (which unchanged code depends on a changed symbol); only the dependencies the changed code itself declares are in scope.

## Milestones
- `plugin-foundation`: Establish the IntelliJ plugin shell and editor panel entry point.
- `change-detection`: Compare a branch against the working directory and locate changed Kotlin declarations.
- `dependency-model`: Build the hierarchical node and directional dependency edge model.
- `graph-rendering`: Render the collapsible dependency graph in an IntelliJ editor panel.
- `mvp-validation`: Validate the end-to-end flow on a representative Kotlin project.

## Constraints
1. Implementation MUST prefer IntelliJ Platform APIs for project structure, PSI, editor integration, and Kotlin symbol inspection when available.
2. Implementation MUST keep git comparison logic separated from Kotlin symbol extraction.
3. Implementation MUST keep graph model construction independent from the UI renderer.
4. Implementation MUST preserve visibility of dependencies when nodes are collapsed by aggregating child edges onto the visible ancestor.
5. Implementation MUST expose enough diagnostic information to explain missing or unresolved symbols during MVP validation.

## Completion
- The campaign is complete when every Milestone above is achieved; each Milestone's own Challenge defines its verification.
