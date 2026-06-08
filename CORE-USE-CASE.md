**Generate a network graph that visualizes Kotlin symbol dependencies for code that changed between a git branch and the working directory, rendered in an IntelliJ plugin editor panel.**

- Compare a specified git branch against the current working directory.
- Identify which Kotlin symbols differ between the two states.
- Represent the codebase as a hierarchical network graph, where nodes span multiple levels of granularity:
    - **Gradle modules**
    - **Packages**
    - **Files**
    - **Classes**
    - **Symbols** (functions, properties, etc.)
- Let the viewer collapse and expand nodes to move between levels — e.g., collapse a module to hide its packages and files, or expand a class to reveal its individual symbols.
- When a node is collapsed, its children's dependencies aggregate onto the collapsed parent, so relationships remain visible at every level.
- Visualize each changed symbol's dependencies in three directions:
    - **Outbound** — how the changed symbols connect to the rest of the existing codebase.
    - **Inbound** — what external code the changed symbols rely on.
    - **Internal** — dependencies among the changed symbols themselves, including relationships that span the different changed files.
- Draw the graph inside an IntelliJ plugin editor panel.
