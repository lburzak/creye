# ADR-014 Analysis Result Caching

## Problem & Context
- Kotlin symbol dependency analysis (ADR-006) runs K2 resolution per analysis run, which is the dominant cost on large Gradle projects (ADR-012).
- A proposal raised adopting an embedded graph database (LadybugDB, a 2025 fork of Kuzu) to (a) persist analysis results as a cache across runs and (b) answer graph queries — collapse aggregation (ADR-008) and view-model projection (ADR-007) — via Cypher instead of in-memory traversal.
- Analysis covers changed code only: ADR-003 scopes comparison to the changed-file set including uncommitted working-directory changes, ADR-004 maps hunks to changed declarations, and ADR-006 resolves only those changed declarations' out-edges. The resulting domain graph is small.
- No caching or persistence ADR exists today; the analysis pipeline is ephemeral and in-memory per run.
- A per-symbol cache variant was also considered: key each changed symbol's resolved out-edges by symbol identity plus a content hash of its declaration range, surviving across runs for symbols that revert to a prior state.

## Constraints
1. The analysis pipeline MUST stay responsive and run long-running work off the UI thread (ADR-012 constraints 1–2).
2. Plugin packaging MUST stay thin enough for a single Gradle module with package-enforced boundaries (ADR-001).
3. Any cross-run cache MUST rest on node identity that is stable across runs; ADR-005 currently guarantees identity only as deterministic *within a single run*.
4. A caching or querying mechanism MUST justify its dependency and complexity against the actual changed-code workload, not a hypothetical whole-project one.

## Decision
- Adopt LadybugDB as an embedded graph store inside the plugin process, serving both caching and querying.
- Persist the domain graph (ADR-007) to LadybugDB so analysis results survive across runs, keyed for incremental reuse; add a per-symbol cache keyed by ADR-005 identity plus a content hash of the declaration range, mapping to that symbol's resolved out-edges.
- Move collapse aggregation (ADR-008) and view-model projection onto Cypher queries against the stored graph instead of in-memory traversal.
- Strengthen ADR-005 node identity to be stable across runs so cache entries remain valid between analyses.

## Rationale
- **Single store for cache and queries.** An embedded graph DB holds the dependency graph in its native shape, so the same store that caches results also answers aggregation and projection queries — no second representation to maintain.
- **In-process, serverless fit.** LadybugDB embeds in the JVM plugin process (Kuzu lineage), needing no external DBMS, matching the plugin deployment model.
- **Reuses expensive resolution.** K2 resolution (ADR-006) is the dominant cost; a per-symbol content-hash cache lets unchanged-state symbols skip re-resolution across runs, branch toggles, and reverts.
- **Cypher expresses graph queries directly.** Collapse aggregation and the render projection are graph traversals; Cypher states them declaratively rather than as hand-written in-memory walks.
- **Headroom.** Columnar storage, vector, and full-text indices leave room to grow toward a whole-project or incremental graph beyond the changed-code scope.

## Rejection reason
The proposed embedded graph database serves no need the current design actually has:
- **Queries buy nothing.** The changed-code-only graph (ADR-003/004/006) is small; in-memory traversal for ADR-008 aggregation and the ADR-007 projection is simpler and faster than serializing to a store and querying via Cypher. Columnar/Cypher features target large analytical graphs that do not exist here (constraint 4).
- **Cross-run caching has low payoff.** ADR-003 includes uncommitted changes, so input mutates on every edit and a whole-run cache invalidates constantly. Per-symbol caching is marginal too: by ADR-006 only changed symbols are resolved — an unchanged symbol is never analyzed (nothing to hit) and a changed symbol's hash differs (miss by definition); reuse occurs only on revert/undo/branch toggle. That granularity pays off only for a whole-project / incremental graph, which is out of current scope.
- **Wrong tool for the cache.** A per-symbol cache is a key→value lookup (identity+hash → out-edges) with no traversal in the cache path; a plain map or disk-backed KV store fits it. The graph DB's traversal and Cypher features go unused, so the dependency is unjustified (constraint 4).
- **Cost and risk against the current shape.** An embedded native columnar DB adds per-platform binary packaging, plugin-size bloat, and startup/I/O cost, working against ADR-001's thin single module (constraint 2) and ADR-012 responsiveness (constraint 1). LadybugDB is a young 2025 fork of a wound-down project — maturity risk.
- **Premature identity change.** A persistent store forces cross-run stable identity now (constraint 3), changing ADR-005 ahead of any scope that needs it.

Revisit as a separate persistence/caching ADR only if scope grows to a persistent whole-project or incremental graph — and even then weigh a plain KV store before a graph database.

## Notes
- Reference: LadybugDB — embedded columnar graph database (Cypher, vector/FTS indices, Parquet/Arrow/DuckDB interop), fork of Kuzu started 2025. https://ladybugdb.com/ , https://github.com/LadybugDB/ladybug
