# Graph Representation
- MUST be force-directed
- MUST be interactive
- MUST be continuous
  - MUST rerender immediately during user interactions
  - MUST rerender while a node is being moved
  - MUST NOT require graph analysis to rerun during render interactions
## Node
- MUST be circle
  - Constant size
  - Color based on diff
- MUST expand on double-click
- MUST collapse itself, and all siblings on right-click
