package pl.lukaszburzak.creye.domain

/**
 * Domain graph produced by analysis (ADR-007 will define the full model).
 * Placeholder until the dependency-model milestone.
 */
data class DependencyGraph(
    val nodes: List<Node> = emptyList(),
) {
    data class Node(val id: String, val label: String)
}
