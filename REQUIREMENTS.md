# Graph Representation
- MUST be force-directed
  - Nodes MUST repulse to avoid stacking
  - Nodes MUST attract related nodes
- MUST be interactive
- MUST be continuous
  - MUST rerender immediately during user interactions
  - MUST rerender while a node is being moved
  - MUST NOT require graph analysis to rerun during render interactions
- MUST be zoomable
## Node
- MUST be circle
  - Constant size
  - Color based on diff
- MUST expand on double-click
  - Children MUST be clustered
  - Expanded node MUST remain visible
  - Children MUST be connected to expanded node via edges
- MUST collapse itself, and all siblings on right-click
## Edge
- Edges of children MUST be conflated on ancestor
- Dependency edges MUST preserve direction, classification, deduplication, and underlying-edge traceability while conflated
- Hierarchy edges MUST be render-derived from visible parent-child structural nodes and MUST be visually distinct from dependency edges
