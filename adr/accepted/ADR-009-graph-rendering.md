# ADR-009 Graph Rendering

## Problem & Context
- The dependency graph must render inside an IntelliJ plugin editor panel.
- The renderer must display hierarchy, dependency edges, changed symbols, related nodes, and unresolved or diagnostic states.
- Rendering technology affects interaction quality, dependency footprint, IntelliJ compatibility, and implementation effort.
- The graph must support collapse, expand, selection, and inspection.
## Constraints
1. The renderer MUST run inside an IntelliJ plugin editor panel.
2. The renderer MUST consume the ADR-007 domain graph and derive its render-facing projection (ADR-008 collapse aggregation) itself, instead of raw git, PSI, or dependency analysis data.
3. The renderer MUST support collapse and expand interactions for hierarchical nodes.
4. The renderer MUST preserve visibility of aggregated dependency edges.
5. The renderer SHOULD expose diagnostics in a way that supports validation.

## Decision
- The renderer MUST be built with Compose Desktop (Compose for Desktop / Skiko), embedded into the IntelliJ editor panel through a Jewel-provided themed Compose panel (`JewelComposePanel`) for Swing interop, rather than a bare Compose `ComposePanel`.
- The renderer MUST treat the ADR-007 domain graph and the collapse state as Compose state, deriving the visible render-facing projection (ADR-008 aggregation) and the rendered tree from them by recomposition, and MUST NOT hold its own copy of git, PSI, or dependency analysis data.
- The collapse state MUST live at the render surface; collapse and expand MUST re-run the ADR-008 projection by recomposition rather than requesting a new analysis from orchestration.
- The graph surface (nodes, aggregated edges, changed and diagnostic states) MUST be drawn in a Compose `Canvas`, with hit-testing, selection, and collapse/expand handled as state transitions over the domain graph and collapse state.
- The renderer MUST use JetBrains Jewel for IDE theming and platform-consistent Compose integration, specifically the Jewel IDE LaF bridge (`org.jetbrains.jewel:jewel-ide-laf-bridge`) so the graph inherits the active IDE theme.
- Compose Multiplatform, Skiko, and Jewel MUST be consumed from the runtime the target IntelliJ Platform (ADR-001's 2025.2 / build `252` baseline) bundles, declared on a non-bundling scope (`compileOnly` / provided), so the platform supplies the single Skiko at runtime. The plugin MUST NOT bundle its own Compose Desktop or Skiko artifacts.
- This is how the pinning is realized: because Compose/Skiko/Jewel come from the platform rather than independent dependency declarations, their versions track the target platform automatically and no Skiko coexistence conflict with the platform-bundled runtime can arise.
- Exact artifact coordinates and versions MUST be declared in the build files against the ADR-001 platform target; this record fixes the source (platform-bundled) and the artifact (Jewel IDE LaF bridge), not the patch versions.

## Rationale
- Compose Desktop keeps the renderer pure Kotlin, matching the project model and the rendering layer's role from ADR-001 as a leaf consuming the domain graph and re-projecting it locally, rather than crossing into a JS runtime (JCEF) where the domain graph would have to be serialized across an IPC boundary and renderer logic would split out of the Kotlin domain.
- A declarative state-to-recomposition model maps directly onto constraints 3 and 4: collapse/expand and edge visibility are state transitions that re-run the ADR-008 projection over the domain graph rather than imperative `Graphics2D` repaint bookkeeping, and selection and inspection fall out of the same state.
- Drawing the graph in a Compose `Canvas` gives immediate-mode control over node and edge rendering, hit-testing, and expand/collapse animation that raw Swing would require building by hand.
- Jewel is JetBrains' official Compose-for-IntelliJ binding, giving IDE theming and the supported integration path that reduces the historical risk of running Compose inside a plugin.
- Consuming Compose/Skiko/Jewel from the platform-bundled runtime on a provided scope, rather than bundling independent Compose Desktop artifacts, is what actually discharges the coexistence risk noted in Problem & Context: a single Skiko exists at runtime, dependency footprint stays near zero, and versions track the platform automatically instead of drifting. The residual cost is that the renderer is tied to whatever Compose/Jewel the target platform ships, accepted in exchange for a Kotlin-native, state-driven renderer; commercial or unmaintained graph libraries (yFiles, JGraphX) were rejected on licensing and maintenance grounds, and a bare custom Swing renderer was rejected for pushing animation and hit-testing into hand-written imperative code.
- The Jewel IDE LaF bridge and its `JewelComposePanel` are chosen over a bare `ComposePanel` because the bridge is the supported entry point that wires Compose to the active IDE theme; using the raw panel would re-implement theming the bridge already provides and lose the platform-consistent integration constraint 2 implies.
