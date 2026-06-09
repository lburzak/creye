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
- Resolve what the changed code depends on at the level of the changed lines and declarations, not whole changed files.
- Visualize each changed symbol's dependencies, classified by where the target lives:
    - **Internal** — unchanged symbols in the same project that the changed code depends on.
    - **External** — symbols in libraries or the JDK that the changed code depends on.
    - **Cohesion** — dependencies among the changed symbols themselves, including relationships that span the different changed files.
- A **related** node is an unchanged node pulled into the graph solely as the target of a changed symbol's internal or external dependency, as opposed to the changed nodes that form the cohesion set.
- Draw the graph inside an IntelliJ plugin editor panel.
