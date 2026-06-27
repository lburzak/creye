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

## View state

- View state MUST survive an editor tab change
  - Includes the expand/collapse frontier, selection, node positions, pan, zoom, simulation sliders, and the Show External toggle
  - View state MUST reset only when analysis re-runs (branch change or refresh)

## Module container

- A module container node MUST parent all of a module's source sets
  - Source sets of one module MUST appear as children of a single module container, not as separate top-level modules

## Node

- MUST display an additional containing-module label when the node is a source set
- Approval percent indicator (e.g. `56%`) MAY be displayed in the center of the node
  - MUST be toggleable
- MUST be circle with constant size
- MUST expand on double-click
  - Children MUST be clustered
  - Expanded node MUST remain visible
  - Children MUST be connected to expanded node via edges
- MUST collapse itself, and all siblings via the node context menu's Collapse action (see Context Menus)
- Color MUST indicate its diff
  - Modified -> Blue
  - Added -> Green
  - Deleted -> Red
  - Any descendant changed -> Blue
- Shape MUST indicate its type
  - Symbol -> Triangle
  - Class/Interface/Enum -> Circle
  - Package -> Square
  - Module -> Diamond
  - Source set -> Hexagon
- Representation MUST NOT render type icons inside nodes
- Clicking a node MUST open the Combined Diff for that node and its descendants
- Graph MUST emphasise the visible node closest to the caret location in Combined Diff with a distinctive halo

## Edge

- Edges of children MUST be conflated on ancestor
- Dependency edges MUST preserve direction, classification, deduplication, and underlying-edge traceability while conflated
- Hierarchy edges MUST be render-derived from visible parent-child structural nodes and MUST be visually distinct from dependency edges

## External nodes

- External nodes represent dependencies on symbols outside the project
- The graph MUST provide a "Show External nodes" toggle
  - When off, external nodes and edges targeting them MUST be hidden
  - When off, internal structure and its dependency edges MUST remain unaffected
  - Default MUST be off

# Diff interpretation

- When file is deleted, its module MUST be resolved to the one it belongs to in comparison target branch

# Requirements

## Goal

Create a graph visualization that feels alive: it continuously evaluates physical forces, propagates movement through connected nodes, and allows subtle global drift without losing readability.

## Normative Language

The key words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT", "MAY", and "OPTIONAL" in this document are to be interpreted as described in RFC 2119.

## Core Behavior

- The graph MUST run as a continuous force simulation rather than a one-time static layout.
- Nodes MUST repel each other to prevent overlap and clustering into a single mass.
- Edges MUST behave like spring-like constraints that pull connected nodes toward each other.
- When one node moves, the effect MUST propagate through its connected neighbors and then outward through the graph.
- Propagated movement SHOULD be damped, so disturbances fade naturally instead of oscillating forever.
- The graph SHOULD preserve enough motion to feel alive while still remaining legible.

## Rippling / Propagation

- The movement propagation behavior SHOULD be treated as damped force propagation through link constraints.
- Dragging, nudging, adding, or otherwise disturbing a node SHOULD affect nearby connected nodes first.
- More distant nodes MAY react indirectly through paths in the graph.
- Propagation SHOULD respect graph structure: strongly or directly connected nodes SHOULD react more visibly than weakly or distantly connected nodes.
- The system SHOULD avoid rigid-body behavior where the whole graph moves as one block after every local change.

## Drift

- The graph MAY exhibit subtle global drift or floating motion.
- Drift SHOULD feel intentional and ambient, not like uncontrolled layout instability.
- The simulation MUST prevent the graph from escaping the viewport.
- Global drift SHOULD be limited by damping, centering, weak gravity, or explicit momentum correction.
- The implementation MUST make drift strength configurable.

## Forces

- The simulation MUST include configurable node repulsion.
- The simulation MUST include configurable edge attraction or spring strength.
- The simulation MUST include configurable damping or velocity decay.
- The simulation MUST include configurable gravity or centering force.
- Collision handling SHOULD be available to reduce node overlap.
- Noise or low-energy perturbation MAY be used to keep the graph alive after it settles.

## Interaction

- Users MUST be able to drag or disturb nodes.
- User interaction SHOULD re-energize the local simulation.
- Released nodes SHOULD transfer motion naturally into the graph instead of snapping immediately into place.
- The graph MUST remain responsive during interaction.

## Stability

- The simulation MUST prevent runaway acceleration.
- The simulation MUST prevent endless violent oscillation.
- The graph SHOULD remain inspectable even while moving.
- Disconnected components MUST NOT drift infinitely away from the main graph.
- The system SHOULD support pausing or freezing the layout when a static view is needed.

## Terminology

- Implementations and documentation SHOULD use "force-directed graph" for the overall technique.
- Implementations and documentation SHOULD use "continuous force simulation" for the living layout behavior.
- Implementations and documentation SHOULD use "damped force propagation" for the ripple-like movement through connected nodes.
- Implementations and documentation SHOULD use "spring-like edge forces" for the mechanism that transfers motion between connected nodes.
- Implementations and documentation SHOULD use "drift" for slow global movement of the whole graph or its components.

# Context Menus

- Right-click MUST open a context menu
  - Right-click on a node MUST open the Node menu
  - Right-click on empty canvas MUST open the Graph canvas menu
- Node
  - Show diff with descendants
  - Go to nearest class
  - Go to nearest package
  - Go to nearest module
  - Expand
  - Collapse
  - Expand down to Classes (if applicable)
  - Expand down to Symbols (if applicable)
- Graph canvas
  - Undo
  - Expand All
  - Collapse All
  - Expand down to Classes
  - Expand down to Symbols

# IntelliJ Actions

- MUST include "Approve selected node"
  - Invocation MUST toggle approval of the currently selected graph node
- MUST include "Scope to selected node"
  - Invocation MUST filter the graph to the selected node and its dependencies (action, not checkbox), for isolation
  - Invocation MUST show the active filter in the graph control row, with a "clear" button
- MUST include "Approve symbol at caret"
  - Invocation MUST toggle approval of the changed symbol nearest the combined-diff caret
  - MUST act on that symbol even when it is collapsed (hidden) in the graph,
    distinguishing it from "Approve selected node"
- MUST include "Collapse module of selected node"
  - Invocation MUST collapse the module subtree containing the selected node, hiding its descendants
- Node selection MUST update on pointer press, not deferred to click release

# Combined Diff View

- MUST be opened side-by-side to graph
- MUST provide a "Toggle approval of symbol with cursor" action
  - The action MUST toggle approval of the changed symbol whose range contains the editor caret
- MUST provide a "Collapse approved" checkbox
  - When checked, fully approved files MUST be collapsed in the combined diff

## Approvals

- Node context menu MUST list "Approved" item with a trailing tick if approved
- Node representation MUST indicate its approval status
- Approval status MUST be persisted
- A node's approval MUST be invalidated when that node's diffed content changes; approvals for unchanged nodes MUST be retained
- Editing a file MUST invalidate affected approvals immediately, without requiring a manual Refresh
- File approval control MUST be displayed in Combined Diff
- Approved code blocks MUST be highlighted in Combined Diff
- A gutter icon MUST indicate approval status for classes/symbols
  - MUST be filled green circle, when approved
  - MUST be unfilled green outline circle, when unapproved
  - MUST toggle approval when clicked
