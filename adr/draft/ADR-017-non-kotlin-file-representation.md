# ADR-017 Non-Kotlin File Representation

## Problem & Context
- The core use case requires that *all* changes in the comparison are visible on the graph, but the current pipeline drops every non-Kotlin change: ADR-003 classifies non-Kotlin changed files and forbids passing them to ADR-004, and ADR-002 ignores non-Kotlin files for hierarchy construction except as diagnostics for unsupported changed files.
- The result is a graph that silently omits changed build scripts, resources, configuration, schema, markup, and other non-Kotlin files, even though they are part of the same change set the user is reviewing.
- These files already exist as data: ADR-003 normalizes them into changed-file records carrying path, previous path, file state, Kotlin/non-Kotlin classification, and content access. What is missing is a rule to turn a non-Kotlin changed-file record into a graph node and, where possible, into edges.
- Non-Kotlin files have no Kotlin PSI, so the ADR-002 declaration hierarchy and the ADR-006 K2 resolution pass do not apply to them. They also frequently lack a Kotlin package directive and may sit outside any Gradle source set.
- References from and between non-Kotlin files (a Gradle script applying another, a resource referenced by path, an include/import directive) are real but format-specific and cannot be resolved with the Kotlin Analysis API. The use case wants them, but only as best-effort.

## Constraints
1. Every changed file in the ADR-003 comparison set MUST be representable on the graph regardless of language; a non-Kotlin changed file MUST NOT be silently dropped.
2. Non-Kotlin file nodes MUST reuse the ADR-007 structural node category and ADR-005 structural-path identity; this record MUST NOT introduce a new node category or a parallel identity scheme.
3. References to non-Kotlin files and between non-Kotlin files SHOULD be inferred best-effort; their absence MUST degrade to a file node carrying its change state with no dependency edges, never to a dropped node or a failed analysis.
4. Best-effort reference inference MUST NOT block on full semantic resolution; an unresolvable reference MUST surface as a diagnostic per ADR-010, not as a node or edge, consistent with ADR-007.
5. This record MUST NOT contradict ADR-002: it governs only how the non-Kotlin *changed-file set* enters the graph, and MUST NOT add a non-Kotlin discovery sweep of the whole project.
6. Representation MUST tolerate non-Kotlin files that lack Gradle module ownership, source-set membership, or any package, mirroring the ADR-002 unresolved-ownership and diagnostic behavior.

## Decision
- Non-Kotlin changed files MUST be materialized as ADR-007 structural **file** nodes, sourced from the non-Kotlin changed-file records ADR-003 already produces. ADR-003's bar on passing them downstream and ADR-002's "ignore for hierarchy" rule are lifted *for the changed-file set only*; whole-project non-Kotlin discovery remains out of scope (constraint 5).
- A non-Kotlin file node MUST be a leaf in the containment tree: it has no package directive and no PSI declaration children, so it carries no class/symbol descendants. Sub-file structure for non-Kotlin formats is explicitly deferred (see Notes).
- Hierarchy placement MUST follow ADR-002's ownership ladder using IntelliJ's project model (`ProjectFileIndex`) rather than Kotlin PSI: place the file under its owning IntelliJ/Gradle module node; when no package directive exists, place it directly under the module (no synthetic package level); when module ownership cannot be determined, place it under the ADR-002 unresolved-ownership node and emit a diagnostic (constraint 6).
- Node identity MUST use the ADR-005 file segment with its module-relative path fallback (non-Kotlin files have no `packageFqName`), folding source set into the segment as ADR-005 already requires. No declaration chain segments are produced.
- Change state MUST be set at file granularity only, from the ADR-003 file state mapped to an ADR-004 change kind (added / modified / deleted / moved). No declaration-level mapping is attempted; the file node is the unit of change for non-Kotlin files.
- Best-effort reference inference, when implemented for a format, MUST run as a separate forward pass over the changed non-Kotlin file's content, producing ADR-007 directed dependency edges only when a referenced target resolves to an already-materialized graph node (a file or symbol the change set contains or that resolves into source content). Edges MUST carry ADR-006 internal/external classification, determined from the target's location, and Kotlin↔non-Kotlin edges in either direction are permitted because both endpoints share ADR-005 structural identity.
- A reference that cannot be resolved to a materialized target MUST be skipped and recorded as a diagnostic per ADR-010 (constraint 4); it MUST NOT mint a node or a dangling edge, consistent with ADR-007's treatment of unresolved references.
- The set of formats that receive reference inference MUST be an explicit, extensible allow-list; a format with no inference rule still yields a file node with change state and no edges (constraint 3).

## Rationale
- The use case driver — all changes visible — is satisfied by the minimal change of admitting the records ADR-003 already builds, rather than by a new pipeline or node category, which keeps constraints 1 and 2 cheap and avoids relitigating ADR-007's structural/external split.
- Reusing the ADR-005 module-relative file segment is exactly the fallback ADR-005 anticipated for files without a package directive, so non-Kotlin file identity converges with Kotlin file identity without a parallel scheme.
- Placement via `ProjectFileIndex` mirrors ADR-002's "IntelliJ project model first" rule and works without PSI, which non-Kotlin files lack; the unresolved-ownership fallback reuses ADR-002's existing escape hatch rather than inventing one (constraint 6).
- Keeping reference inference as an optional, format-scoped forward pass that only emits edges to already-materialized targets mirrors ADR-006's forward-only, skip-on-unresolved discipline and ADR-007's "no nodes/edges for unresolved references" rule, so best-effort inference cannot expand the graph beyond the changed-code neighborhood or leak fake objects (constraints 3, 4).
- Deferring sub-file structure keeps this record about *whether and where non-Kotlin files appear and connect*, leaving per-format parsing to later, narrower records rather than committing the model to a structure it cannot yet resolve.

## Notes
- Open for this draft: the concrete initial allow-list of formats for reference inference (likely candidates: Gradle build scripts via `apply`/`include`/project paths, resource references by path) and the resolution heuristic per format.
- Deferred: sub-file granularity for non-Kotlin files (e.g. tasks within a Gradle script, keys within a config). If pursued, it would need its own ADR-005 segment rules and an ADR-004-style change-attribution rule, and should not be folded into this record.
- This record assumes ADR-003 already tags Kotlin/non-Kotlin classification and exposes content for non-Kotlin records; if that classification proves insufficient for placement (e.g. generated or out-of-module files), revisit alongside ADR-002.
