# ADR-013 Force-Directed Graph Layout Engine

## Problem & Context
- The dependency graph must be force-directed so node proximity reflects dependency topology rather than containment order alone.
- ADR-009 fixes Compose Canvas as the renderer, but it does not decide the layout algorithm that produces node coordinates for that canvas.
- ADR-007 and ADR-008 make the domain graph and collapsed visible graph the source of truth; layout must be a render-facing projection over that graph, not a second graph model.
- The current deterministic shelf layout is useful as a simple baseline, but it does not use dependency edges to shape node placement, cluster related code, or reduce visual edge noise.
- A custom force-directed engine would need collision handling, adaptive movement, graph-size tuning, and performance safeguards before it could be trusted inside an IntelliJ plugin.

## Constraints
1. The layout engine MUST run in the JVM process used by the IntelliJ plugin.
2. The layout engine MUST consume the ADR-008 visible graph projection and MUST NOT consume git, PSI, dependency-analysis, or uncollapsed domain-graph internals directly.
3. The layout engine MUST produce render coordinates only; ADR-007 remains the source of truth for nodes, edges, identity, and diagnostics.
4. Layout computation MUST run off the UI thread and MUST be bounded by an iteration, time, or cancellation limit so it cannot freeze the IDE.
5. Layout output SHOULD be stable for the same visible graph and layout profile by using deterministic graph insertion, deterministic initial coordinates, and a bounded simulation profile.
6. Edge direction and classification MUST remain render metadata even if the force simulation treats edge attraction symmetrically.
7. Gephi Toolkit license obligations MUST be explicit before the plugin is distributed.

## Decision
- The force-directed layout engine MUST use Gephi Toolkit's ForceAtlas2 implementation, consumed through the JVM Gephi Toolkit dependency, rather than a custom force simulation or a browser-based graph runtime.
- Gephi Toolkit MUST be used only behind a project-local layout adapter that accepts `VisibleGraph` and returns `GraphLayout`. Gephi graph objects, workspace objects, and layout APIs MUST NOT leak into the domain graph, rendering canvas, orchestration, or tests outside the layout package.
- The adapter MUST map visible structural and external nodes to Gephi nodes using stable IDs derived from `GraphNodeId`.
- The adapter MUST map visible dependency edges to Gephi edges using ADR-008 visible edges. Edge weights SHOULD be derived from the count of underlying domain edge keys when available, otherwise defaulting to `1.0`.
- The adapter MUST initialize node positions deterministically before running ForceAtlas2. Initial positions MAY use the existing shelf layout as a seed so repeated inputs start from a stable, readable baseline.
- ForceAtlas2 MUST run with a bounded project-owned layout profile: fixed iteration or time budget, gravity enabled for disconnected components, edge-weight influence enabled, and Barnes-Hut optimization enabled for non-trivial graphs. Exact numeric constants MAY be tuned without a new ADR when the engine and integration boundary remain unchanged.
- ForceAtlas2 layout MUST be executed outside the Compose draw phase and outside the Swing event dispatch thread. Compose MUST render only the completed `GraphLayout` state, consistent with ADR-009.
- Layout failures, cancellations, unsupported graph states, or dependency initialization failures MUST surface through rendering diagnostics per ADR-010 rather than throwing through the UI.
- Gephi Toolkit MUST be consumed under its CDDL 1.0 license option for project distribution. The GPLv3 option MUST NOT be selected implicitly or by accident.
- Plugin distributions that include Gephi Toolkit MUST include the upstream Gephi license notice and the CDDL 1.0 and GPLv3 license texts required by the upstream notice.
- Before any plugin distribution that bundles Gephi Toolkit, the release process MUST audit Gephi Toolkit and its transitive runtime dependencies for license compatibility with the intended plugin distribution model.
- Distribution MUST be blocked until any incompatible, GPL-only, unknown, or missing transitive license finding is resolved by exclusion, replacement, documented compatibility, or a new ADR.
- The implementation MUST NOT copy or modify Gephi source code in this repository. If modifying Gephi code becomes necessary, that work MUST be isolated and the CDDL source-availability obligations for the modified covered files MUST be documented before distribution.

## Rationale
- ForceAtlas2 is already designed for exploratory network visualization and for the scale-free graphs common in dependency networks. It includes practical behavior the project would otherwise have to implement and tune, including gravity, weighted edges, Barnes-Hut optimization, and adaptive movement.
- Gephi Toolkit packages Gephi graph and layout modules as a Java library, so it can run in the IntelliJ plugin JVM while preserving ADR-009's Compose Canvas renderer. This avoids introducing a JCEF, JavaScript, Python, or external-process runtime just to compute coordinates.
- Using ForceAtlas2 as a layout engine does not make Gephi the renderer or graph model. The adapter boundary keeps ADR-007, ADR-008, and ADR-009 intact: the domain graph is projected to a visible graph, Gephi computes coordinates, and Compose draws the result.
- Seeding ForceAtlas2 from a deterministic baseline and running a bounded profile balances force-directed readability with repeatability. The layout can use topology without making UI tests depend on an open-ended simulation.
- The local adapter contains the main risks of Gephi Toolkit: dependency footprint, NetBeans-era APIs, licensing, and future replacement. If ForceAtlas2 becomes unsuitable, the rest of the renderer should only depend on `GraphLayout`.
- Gephi Toolkit is dual-licensed under CDDL 1.0 and GPLv3. Choosing the CDDL option keeps the expected obligation at the covered-file level rather than turning the plugin distribution into a GPLv3 whole-work decision, but it still requires preserving notices, shipping license texts, and making covered source available as required. A transitive license audit is required because Gephi Toolkit packages multiple Gephi modules into one Java dependency rather than exposing only ForceAtlas2 as a small standalone artifact.

## Notes
Rejected alternatives, kept so they are not relitigated:
- **Keep the deterministic shelf layout as the final layout.** Rejected because it satisfies containment readability but not the force-directed requirement; dependency edges do not influence placement.
- **Implement a custom ForceAtlas2-style solver.** Rejected because the project would own algorithmic tuning, performance safeguards, and layout quality before the graph UI delivers its core value.
- **Use D3-force or another browser/JavaScript force engine.** Rejected because it would introduce a browser runtime or cross-runtime serialization boundary that ADR-009 intentionally avoids.
- **Use Graphviz through an external process.** Rejected because it adds a process dependency and file/IPC boundary, and it does not fit interactive recomputation inside the IntelliJ editor panel.
- **Use Gephi Toolkit as the renderer or authoritative graph model.** Rejected because it would contradict ADR-007 and ADR-009; Gephi is selected only for coordinate computation.

Sources checked while drafting:
- Gephi project README and Toolkit description: https://github.com/gephi/gephi
- Gephi Toolkit README and license declaration: https://github.com/gephi/gephi-toolkit
- Gephi license notice and dual-license text: https://github.com/gephi/gephi/blob/master/COPYING.txt
- CDDL 1.0 distribution obligations: https://github.com/gephi/gephi/blob/master/cddl-1.0.txt
- Maven Central metadata for `org.gephi:gephi-toolkit`: https://central.sonatype.com/artifact/org.gephi/gephi-toolkit
- Javadoc metadata for `org.gephi:gephi-toolkit`: https://javadoc.io/doc/org.gephi/gephi-toolkit/latest/index.html
- Jacomy et al. 2014, "ForceAtlas2, a Continuous Graph Layout Algorithm for Handy Network Visualization Designed for the Gephi Software": https://doi.org/10.1371/journal.pone.0098679
