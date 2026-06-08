# Kotlin Symbol Dependency Graph MVP

## Goal
- Generate a network graph that visualizes Kotlin symbol dependencies for code changed between a specified git branch and the current working directory, rendered in an IntelliJ plugin editor panel.

## Scope
- The MVP MUST compare one configured git branch against the current working directory.
- The MVP MUST identify changed Kotlin symbols at file, class, function, and property granularity where feasible.
- The MVP MUST construct a hierarchical graph spanning Gradle modules, packages, files, classes, and symbols.
- The MVP MUST support collapse and expand behavior that aggregates child dependencies onto collapsed parent nodes.
- The MVP MUST classify dependency edges as outbound, inbound, or internal relative to changed symbols.
- The MVP MUST render the graph inside an IntelliJ plugin editor panel.

## Out of Scope
- The MVP MUST NOT require full semantic parity with the IntelliJ dependency analyzer.
- The MVP MUST NOT require perfect symbol resolution for generated sources, multiplatform source sets, or unresolved project states.
- The MVP MUST NOT require graph persistence across IDE restarts.
- The MVP MUST NOT require remote collaboration, export, or sharing features.

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

## Challenge
- Given a Kotlin Gradle project with changes in at least two files, the plugin MUST open an editor panel that displays changed symbols and their inbound, outbound, and internal dependencies.
- Collapsing a file, package, or module node MUST hide descendants while preserving aggregate edges connected to the collapsed node.
- Expanding a class node MUST reveal changed and related symbols beneath it.
- The graph MUST include dependencies among changed symbols across different files.
- The validation project and exact commands or manual IDE steps MUST be documented before campaign completion.
