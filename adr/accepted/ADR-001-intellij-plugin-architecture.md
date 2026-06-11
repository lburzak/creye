# ADR-001 IntelliJ Plugin Architecture

## Problem & Context
- The plugin must provide an IntelliJ editor panel that displays Kotlin symbol dependency graphs.
- The plugin needs a clear entry point for launching analysis from the IDE.
- IDE integration, analysis orchestration, graph modeling, and graph rendering have different responsibilities and failure modes.
- Without explicit boundaries, plugin code can couple IntelliJ action handling to git comparison, Kotlin PSI traversal, dependency analysis, and rendering.
## Constraints
1. The implementation MUST use IntelliJ Platform APIs for plugin actions, project access, background execution, and editor integration where practical.
2. The implementation MUST keep analysis orchestration separate from UI rendering.
3. The implementation MUST keep graph model construction independent from the editor panel.
4. The implementation MUST support cancellation for long-running analysis.
5. The implementation SHOULD keep plugin lifecycle code thin enough that analysis behavior can be tested independently.
## Decision
- The plugin MUST be organized into four layers with dependencies pointing inward: IDE integration, orchestration, domain, and rendering.
- The IDE integration layer MUST contain only IntelliJ entry points and platform access, and MUST NOT contain git comparison, PSI traversal, dependency resolution, graph construction, or rendering logic.
- Analysis MUST be launched from an `AnAction` that opens the graph in an editor panel through a `FileEditorProvider`.
- The orchestration layer MUST be a project-level IntelliJ service that runs the analysis pipeline and produces the domain graph model governed by ADR-007 as its analysis output. The render-facing projection (collapse aggregation per ADR-008) MUST be derived by the rendering layer from that domain graph and the interactive collapse state; orchestration MUST NOT re-run per collapse or expand interaction.
- The orchestration service MUST run long-running analysis off the UI thread using a plugin-owned coroutine scope, and MUST support cancellation.
- The domain layer MUST NOT depend on the rendering layer or the IDE integration layer's entry points.
- The rendering layer MUST consume only the ADR-007 domain graph and MUST derive its render-facing projection from it per ADR-008; it MUST NOT access git, PSI, or dependency resolution data directly.
- The orchestration service MUST be invocable independently of the `AnAction` and `FileEditorProvider` so analysis behavior can be tested without UI.
- All layers MUST reside in a single Gradle module, with layer boundaries enforced by package structure.

## Rationale
- Four inward-pointing layers give each responsibility from Problem & Context its own seam, satisfying constraints 2 and 3 by construction rather than convention.
- An `AnAction` plus `FileEditorProvider` is the idiomatic IntelliJ path for a launchable editor panel (constraint 1), keeping the graph in an editor panel rather than a tool window.
- A project-level service is the platform's natural unit for lifecycle, dependency access, and a cancellable coroutine scope, serving constraints 1 and 4; making it invocable without UI serves constraint 5.
- A coroutine scope over `Task.Backgroundable` keeps cancellation and structured concurrency explicit while still moving work off the UI thread (constraint 4).
- Making the domain graph the orchestration output and deriving the render-facing projection in the rendering layer keeps graph model construction independent of the panel (constraint 3) and prevents the renderer from coupling to git or PSI (constraint 2), while letting collapse and expand re-project locally without an orchestration round-trip per interaction.
- A single Gradle module with package-enforced boundaries avoids multi-module ceremony that is not currently warranted; the layer rules above still keep boundaries explicit, and a module split remains open if compile-time enforcement becomes worthwhile later.
