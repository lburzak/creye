# Milestone: plugin-foundation

Establish the IntelliJ plugin shell and editor panel entry point per ADR-001 (architecture, toolchain) and ADR-009 (Compose/Jewel rendering surface).

## Definition of Ready

- ADR-001 and ADR-009 MUST be accepted (they are).
- JDK 21 MUST be available for the Gradle toolchain.

## Definition of Done

- The repository MUST contain a single-module Gradle build using the IntelliJ Platform Gradle Plugin 2.x targeting IntelliJ IDEA 2025.2 (`sinceBuild` 252, open `untilBuild`), JDK 21 toolchain, Kotlin 2.1.x, Gradle 8.x wrapper.
- The plugin MUST declare the four ADR-001 layer packages (ide, orchestration, domain, rendering) with dependencies pointing inward.
- An `AnAction` MUST open a graph editor panel through a `FileEditorProvider`.
- The editor panel MUST embed a Jewel-themed Compose surface (`JewelComposePanel`) sourced from the platform-bundled Compose/Jewel runtime on a non-bundling scope.
- A project-level orchestration service MUST exist with a plugin-owned coroutine scope, cancellable, invocable without UI.
- `./gradlew buildPlugin` MUST succeed.

## Tasks

- `gradle-plugin-scaffold` — Gradle wrapper, build files, plugin.xml, toolchain baseline from ADR-001.
- `layered-package-skeleton` — four layer packages plus the project-level orchestration service with cancellable coroutine scope.
- `editor-panel-entry` — `AnAction` + `FileEditorProvider` opening a stub Jewel Compose panel.

## Challenge

Idempotent verification of achievement:

- [x] `./gradlew buildPlugin` exits 0.
- [x] `./gradlew verifyPluginStructure` (or `verifyPlugin`) reports no errors.
- [ ] Manual: `./gradlew runIde`, open any project, invoke the "Show Dependency Graph" action, confirm an editor tab opens rendering the Compose stub surface themed like the IDE.
