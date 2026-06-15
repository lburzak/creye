package pl.lukaszburzak.creye.domain.identity

/**
 * Structural containment-path node identity (ADR-005). Value equality over the full
 * segment list is the sole equality key; identity is run-local and never persisted.
 */
data class NodePath(val segments: List<NodeSegment>)

/** True when this path lies strictly below [ancestor] in the containment hierarchy. */
fun NodePath.isDescendantOf(ancestor: NodePath): Boolean =
    segments.size > ancestor.segments.size &&
        segments.take(ancestor.segments.size) == ancestor.segments

/** The owning file segment of this path, or null for Module/Package paths above the file level. */
fun NodePath.fileSegment(): NodeSegment.File? =
    segments.filterIsInstance<NodeSegment.File>().firstOrNull()

sealed interface NodeSegment {
    data class Module(val id: String) : NodeSegment

    /** Uses [DEFAULT_PACKAGE] sentinel when no package is declared. */
    data class Package(val fqName: String) : NodeSegment

    /** Module-relative path disambiguates same-named files across source roots (ADR-005). */
    data class File(val name: String, val moduleRelativePath: String) : NodeSegment

    data class Class(val name: String) : NodeSegment

    /** Function or property; [discriminator] is non-null only for callable declarations. */
    data class Symbol(val name: String, val discriminator: CallableDiscriminator?) : NodeSegment

    companion object {
        const val DEFAULT_PACKAGE = "<default>"
    }
}

/**
 * Overload discriminator (ADR-005): arity plus type-reference text taken verbatim from
 * PSI, never resolved types. Return type excluded — Kotlin forbids overloading on it.
 */
data class CallableDiscriminator(
    val arity: Int,
    val parameterTypeTexts: List<String>,
    val receiverTypeText: String?,
)
