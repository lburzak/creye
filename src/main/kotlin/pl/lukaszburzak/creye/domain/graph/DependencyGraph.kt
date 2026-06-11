package pl.lukaszburzak.creye.domain.graph

import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment

enum class DependencyClassification { INTERNAL, EXTERNAL, COHESION }

enum class DependencyKind { CALL, PROPERTY_ACCESS, TYPE_REFERENCE }

data class ExternalSymbolId(val id: String, val displayName: String)

sealed interface GraphNodeId {
    data class Structural(val path: NodePath) : GraphNodeId
    data class External(val id: ExternalSymbolId) : GraphNodeId
}

data class StructuralNode(
    val path: NodePath,
    val displayName: String,
    val change: ChangeKind?,
)

data class ExternalNode(val id: ExternalSymbolId)

data class DependencyEdge(
    val source: NodePath,
    val target: GraphNodeId,
    val classification: DependencyClassification,
    val kind: DependencyKind,
)

data class DependencyGraph(
    val structuralNodes: List<StructuralNode> = emptyList(),
    val externalNodes: List<ExternalNode> = emptyList(),
    val edges: List<DependencyEdge> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
)

fun NodePath.displayName(): String = when (val segment = segments.last()) {
    is NodeSegment.Module -> segment.id
    is NodeSegment.Package -> segment.fqName
    is NodeSegment.File -> segment.name
    is NodeSegment.Class -> segment.name
    is NodeSegment.Symbol -> segment.name
}
