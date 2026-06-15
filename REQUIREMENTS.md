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

- MUST be circle with constant size
- MUST expand on double-click
  - Children MUST be clustered
  - Expanded node MUST remain visible
  - Children MUST be connected to expanded node via edges
- MUST collapse itself, and all siblings via the node context menu's Collapse action (see Context Menus)
- MUST contain an icon, indicating it's type
- Color MUST indicate it's diff
  - Modified -> Blue
  - Added -> Green
  - Deleted -> Red
  - Any descendant changed -> Blue
- Shape MUST indicate it's type
  - Symbol -> Triangle
  - Class/Interface/Enum -> Circle
  - Package -> Square
  - Module -> Diamond

## Edge

- Edges of children MUST be conflated on ancestor
- Dependency edges MUST preserve direction, classification, deduplication, and underlying-edge traceability while conflated
- Hierarchy edges MUST be render-derived from visible parent-child structural nodes and MUST be visually distinct from dependency edges

# Diff interpretation

- When file is deleted, it's module MUST be resolved to the one it belongs to in comparison target branch

# Living Graph

Create a graph visualization that feels alive: it continuously evaluates physical forces, propagates movement through connected nodes, and allows subtle global drift without losing readability.

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