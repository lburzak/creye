# ADR-007 Graph Model

## Problem & Context
- The graph must represent both hierarchy and dependencies.
- The graph hierarchy includes modules, packages, files, classes, and symbols.
- The dependency model must remain independent from the UI renderer.
- Diagnostics need to remain connected to the graph elements they explain.
## Constraints
1. Graph model construction MUST be independent from the UI renderer.
2. The graph model MUST represent hierarchical containment separately from dependency relationships.
3. The graph model MUST represent changed, related, unresolved, and structural nodes where needed.
4. Dependency edges MUST include direction and classification metadata.
5. Diagnostics MUST be attachable to the graph, nodes, or edges where applicable.
