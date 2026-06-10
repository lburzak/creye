# ADR-007 Graph Model

## Problem & Context
- The graph must represent both hierarchy and dependencies.
- The graph hierarchy includes modules, packages, files, classes, and symbols.
- The dependency model must remain independent from the UI renderer.
- This record governs the domain graph model. The render-facing graph view model consumed by ADR-001 and ADR-009 is a projection derived from this model by orchestration (applying collapse aggregation per ADR-008); it is not a second source of truth.
- Diagnostics need to remain connected to the graph elements they explain.
## Constraints
1. Graph model construction MUST be independent from the UI renderer.
2. The graph model MUST represent hierarchical containment separately from dependency relationships.
3. The graph model MUST represent changed, related, unresolved-reference, structural-scaffolding, and external-target conditions without requiring each condition to be a distinct node kind.
4. Dependency edges MUST include direction and classification metadata.
5. Diagnostics MUST be attachable to the graph, nodes, or edges where applicable.

## Decision
- The domain graph model MUST be the source of truth for hierarchy, dependency edges, node identity, and diagnostic attachment. Render-facing graph view models MUST be derived projections, not separate sources of truth.
- The graph model MUST have two node categories: structural nodes and external nodes.
- Structural nodes MUST represent modules, packages, files, classes, and symbols. They MUST use ADR-005 structural-path identity and live in the containment tree.
- External nodes MUST represent resolved library or dependency endpoints. They MUST use the external semantic identity available from dependency resolution, such as `ClassId`, `CallableId`, or fqName fallback captured within the ADR-006 analysis session, and MUST live outside the structural containment tree.
- Changed state MUST be stored only as intrinsic state on structural nodes and MUST carry the ADR-004 change kind. Related, structural-scaffolding, and unresolved-reference conditions MUST be derived from edges, graph materialization, and diagnostics.
- A structural node MUST be treated as related when it is the target of one or more dependency edges. A materialized structural node with no changed state and no related condition MUST be treated as structural scaffolding.
- Unresolved references MUST NOT create nodes or edges. They MUST surface as diagnostics attached to the source structural node that owns the unresolved reference.
- Dependency edges MUST be directed from source to target, where the source is changed code declaring a dependency. Edges MUST carry classification metadata from ADR-006 and any dependency-kind metadata represented by the graph model.
- The graph MUST materialize the minimal changed-code neighborhood: changed structural nodes from ADR-004, resolved internal/cohesion target nodes from ADR-006, and the full containment ancestor chain for each included structural node up to the module.
- Materializing an ancestor MUST NOT imply materializing its siblings or descendants. Non-changed internal targets MUST receive the same ancestor chain as changed nodes so ADR-008 collapse aggregation has stable visible ancestors on both source and target sides.
- Diagnostics MUST attach to the most specific existing graph element. Graph-level diagnostics MUST use a graph attachment when no reliable node or edge exists. Node diagnostics MUST use graph node identity. Edge diagnostics MUST be allowed only for existing edges and MUST be keyed by the canonical domain edge identity.
- Rendered or collapsed edge diagnostics MUST retain traceability to underlying domain edge keys rather than defining separate attachment identities.

## Rationale
- Keeping the domain graph independent from the renderer preserves the boundary required by ADR-001, ADR-008, and ADR-009: rendering can collapse, mark, and aggregate without becoming the authoritative model.
- Separating structural and external nodes avoids forcing library endpoints into ADR-005 structural identity, which only converges for symbols with source declarations.
- Treating changed as stored state and related, structural-scaffolding, and unresolved-reference as derived conditions avoids conflicting node enums. A changed symbol can also be related through cohesion, and unresolved references have no stable target identity.
- Modeling unresolved references as diagnostics preserves ADR-006's rule to skip unresolved edges while still making missing dependency evidence visible.
- Materializing full ancestor chains for included structural nodes gives collapse aggregation stable endpoints without expanding the graph into the whole project.
- Attaching diagnostics to existing graph elements prevents fake unresolved nodes or missing-target edges from leaking into the domain model.

## Notes
Rejected alternatives, kept so they are not relitigated:
- **Modeling changed, related, structural, and unresolved as exclusive node kinds.** Rejected because the conditions are orthogonal: a changed symbol can also be related through a cohesion edge, structural scaffolding is the absence of changed and related state on a materialized ancestor, and unresolved references have no target identity.
- **Representing external targets as structural nodes.** Rejected because ADR-005 structural identity only converges for symbols with source declarations. External targets are resolved symbols without source declarations, so they need semantic identity and must remain outside the containment tree. Rendering may group them under a synthetic external root or library grouping, but that grouping is not structural identity.
- **Representing unresolved references as nodes or edges.** Rejected because unresolved references have no resolved symbol, source PSI target, edge classification, or mintable target identity. They remain diagnostics on the source structural node, carrying evidence such as reference text and location.
- **Materializing whole packages, files, or subtrees around changed code.** Rejected because the graph is the changed-code neighborhood, not a whole-project graph. Full ancestor chains are required for collapse aggregation, but siblings and descendants are not.
- **Defining diagnostic keys for missing targets or rendered edges.** Rejected because diagnostics attach to existing graph elements. Missing targets would create fake graph objects, and rendered or collapsed edges are projections that should retain traceability to domain edge keys.
