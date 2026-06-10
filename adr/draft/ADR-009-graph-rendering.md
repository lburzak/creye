# ADR-009 Graph Rendering

## Problem & Context
- The dependency graph must render inside an IntelliJ plugin editor panel.
- The renderer must display hierarchy, dependency edges, changed symbols, related nodes, and unresolved or diagnostic states.
- Rendering technology affects interaction quality, dependency footprint, IntelliJ compatibility, and implementation effort.
- The graph must support collapse, expand, selection, and inspection.
## Constraints
1. The renderer MUST run inside an IntelliJ plugin editor panel.
2. The renderer MUST consume the graph view model (the render-facing projection of ADR-007's graph model produced by orchestration) instead of raw git, PSI, or dependency analysis data.
3. The renderer MUST support collapse and expand interactions for hierarchical nodes.
4. The renderer MUST preserve visibility of aggregated dependency edges.
5. The renderer SHOULD expose diagnostics in a way that supports validation.

## Decision
- The renderer MUST be built with Compose Desktop (Compose for Desktop / Skiko), embedded into the IntelliJ editor panel through a `ComposePanel` Swing interop component.
- The renderer MUST treat the graph view model as Compose state, deriving the rendered tree from it by recomposition, and MUST NOT hold its own copy of git, PSI, or dependency analysis data.
- The graph surface (nodes, aggregated edges, changed and diagnostic states) MUST be drawn in a Compose `Canvas`, with hit-testing, selection, and collapse/expand handled as state transitions over the view model.
- The renderer MUST use JetBrains Jewel for IDE theming and platform-consistent Compose integration.
- The Compose and Skiko dependencies MUST be pinned against the target IntelliJ Platform version to avoid Skiko coexistence conflicts with the platform-bundled runtime.

## Rationale
- Compose Desktop keeps the renderer pure Kotlin, matching the project model and the rendering layer's role from ADR-001 as a leaf consuming the view model, rather than crossing into a JS runtime (JCEF) where the view model would have to be serialized across an IPC boundary and renderer logic would split out of the Kotlin domain.
- A declarative state-to-recomposition model maps directly onto constraints 3 and 4: collapse/expand and edge visibility are state transitions over the view model rather than imperative `Graphics2D` repaint bookkeeping, and selection and inspection fall out of the same state.
- Drawing the graph in a Compose `Canvas` gives immediate-mode control over node and edge rendering, hit-testing, and expand/collapse animation that raw Swing would require building by hand.
- Jewel is JetBrains' official Compose-for-IntelliJ binding, giving IDE theming and the supported integration path that reduces the historical risk of running Compose inside a plugin.
- Pinning Compose/Skiko against the target platform addresses the dependency-footprint and IntelliJ-compatibility concerns from Problem & Context: the cost is the bundled Skiko native binaries and a version-coexistence risk with the platform's own Skiko, accepted in exchange for a Kotlin-native, state-driven renderer; commercial or unmaintained graph libraries (yFiles, JGraphX) were rejected on licensing and maintenance grounds, and a bare custom Swing renderer was rejected for pushing animation and hit-testing into hand-written imperative code.
