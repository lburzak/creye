# ADR-001 IntelliJ Plugin Architecture

## Problem & Context
- The plugin must provide an IntelliJ editor panel that displays Kotlin symbol dependency graphs.
- The plugin needs a clear entry point for launching analysis from the IDE.
- IDE integration, analysis orchestration, graph modeling, and graph rendering have different responsibilities and failure modes.
- Without explicit boundaries, plugin code can couple IntelliJ action handling to git comparison, Kotlin PSI traversal, dependency analysis, and rendering.
- The four layers share one Gradle module, so that module needs a concrete platform target and build toolchain before any layer can be built; this record owns that baseline.
- ADR-006 mandates the K2 Kotlin Analysis API (`analyze { }`), which constrains the minimum viable platform target; an arbitrarily old platform cannot satisfy it.

## Constraints
1. The implementation MUST use IntelliJ Platform APIs for plugin actions, project access, background execution, and editor integration where practical.
2. The implementation MUST keep analysis orchestration separate from UI rendering.
3. The implementation MUST keep graph model construction independent from the editor panel.
4. The implementation MUST support cancellation for long-running analysis.
5. The implementation SHOULD keep plugin lifecycle code thin enough that analysis behavior can be tested independently.
6. The platform target MUST be one where the K2 Kotlin Analysis API required by ADR-006 is the stable default frontend.
7. The build toolchain MUST be the JetBrains-supported path for the chosen platform target, and MUST NOT adopt an obsolete one.
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
- The platform target MUST be IntelliJ IDEA 2025.2 (build branch `252`) as the baseline: `sinceBuild` MUST be `252`, and `untilBuild` MUST be left open. This satisfies constraint 6, since K2 is the stable default frontend from 2024.3 onward.
- The build MUST use the IntelliJ Platform Gradle Plugin 2.x (`org.jetbrains.intellij.platform`); the obsolete Gradle IntelliJ Plugin 1.x (`org.jetbrains.intellij`) MUST NOT be used (constraint 7).
- The Kotlin Analysis API required by ADR-006 MUST be obtained from the platform's bundled Kotlin support (`bundledPlugin("org.jetbrains.kotlin")`), not a separately versioned Kotlin compiler frontend.
- The toolchain MUST be: JDK 21 (Kotlin/Java toolchain and JVM target), Kotlin 2.1.x with `apiVersion`/`languageVersion` 2.1, and Gradle 8.x via the wrapper.
- The build MUST declare a Gradle JVM toolchain pinned to JDK 21 (e.g. `kotlin { jvmToolchain(21) }` / `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`) so compilation runs against JDK 21 regardless of the developer's default `JAVA_HOME`, with Gradle toolchain auto-provisioning permitted to supply it.
- The Kotlin stdlib MUST NOT be bundled with the plugin; the platform supplies a compatible one.
- Exact dependency and tool versions MUST be declared in the build files (`gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`) and plugin metadata in `plugin.xml`; this record fixes the baseline and policy, not the patch versions.

## Rationale
- Four inward-pointing layers give each responsibility from Problem & Context its own seam, satisfying constraints 2 and 3 by construction rather than convention.
- An `AnAction` plus `FileEditorProvider` is the idiomatic IntelliJ path for a launchable editor panel (constraint 1), keeping the graph in an editor panel rather than a tool window.
- A project-level service is the platform's natural unit for lifecycle, dependency access, and a cancellable coroutine scope, serving constraints 1 and 4; making it invocable without UI serves constraint 5.
- A coroutine scope over `Task.Backgroundable` keeps cancellation and structured concurrency explicit while still moving work off the UI thread (constraint 4).
- Making the domain graph the orchestration output and deriving the render-facing projection in the rendering layer keeps graph model construction independent of the panel (constraint 3) and prevents the renderer from coupling to git or PSI (constraint 2), while letting collapse and expand re-project locally without an orchestration round-trip per interaction.
- A single Gradle module with package-enforced boundaries avoids multi-module ceremony that is not currently warranted; the layer rules above still keep boundaries explicit, and a module split remains open if compile-time enforcement becomes worthwhile later.
- The 2025.2 baseline is the cheapest target that satisfies constraint 6: K2 is the stable default from 2024.3, and 2025.2 is a current release at decision time, so the `analyze { }` API ADR-006 builds on is present and supported. An open `untilBuild` avoids re-pinning the ceiling on every platform bump for an MVP.
- The Platform Gradle Plugin 2.x is mandatory rather than preferred because JetBrains marks the 1.x plugin obsolete for 2024.2+ targets; choosing it would adopt a sunset path (constraint 7), mirroring ADR-006's rejection of the deprecated K1 frontend.
- Taking Kotlin support from the bundled `org.jetbrains.kotlin` plugin keeps the Analysis API version locked to the platform, avoiding a frontend-version skew between the plugin's resolution code and the IDE it runs in.
- JDK 21 is the toolchain the 252 platform branch requires; Kotlin 2.1.x and Gradle 8.x are the versions the Platform Gradle Plugin 2.x pairs with for that branch. A declared Gradle JVM toolchain pins that JDK in the build itself, so compilation is reproducible across machines and CI regardless of each developer's default `JAVA_HOME`, rather than depending on the ambient JDK. Fixing the baseline here rather than in a separate record keeps the single-module build's foundation with the architecture that mandates the single module, while leaving patch versions to the build files so routine bumps need no ADR change.
