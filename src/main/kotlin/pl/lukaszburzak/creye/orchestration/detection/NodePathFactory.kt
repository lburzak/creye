package pl.lukaszburzak.creye.orchestration.detection

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.identity.CallableDiscriminator
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment

/** Module context for the file segment; supplied by the caller (ADR-002 seam). */
data class FileSegmentContext(
    val moduleId: String,
    val moduleRelativePath: String,
    val diagnostics: List<Diagnostic> = emptyList(),
)

/**
 * Mints ADR-005 structural-path identity from PSI text only — no resolved types,
 * no fully-qualified names beyond the declared package.
 */
object NodePathFactory {

    fun filePath(file: KtFile, context: FileSegmentContext, fileName: String): NodePath {
        val packageName = file.packageFqName.asString().ifEmpty { NodeSegment.DEFAULT_PACKAGE }
        return NodePath(
            moduleSegments(context.moduleId) +
                listOf(
                    NodeSegment.Package(packageName),
                    NodeSegment.File(fileName, context.moduleRelativePath),
                ),
        )
    }

    /**
     * Splits a Gradle module id into a module container and an optional source-set child.
     * Gradle's external project id for a per-source-set module is `<projectPath>:<sourceSet>`
     * (e.g. `creye:main`), so the last `:`-separated token is the source set. Ids without a
     * `:` separator (the IntelliJ-module-name fallback) stay a single module container.
     */
    private fun moduleSegments(moduleId: String): List<NodeSegment> {
        val separator = moduleId.lastIndexOf(':')
        if (separator <= 0 || separator == moduleId.length - 1) {
            return listOf(NodeSegment.Module(moduleId))
        }
        return listOf(
            NodeSegment.Module(moduleId.substring(0, separator)),
            NodeSegment.SourceSet(moduleId.substring(separator + 1)),
        )
    }

    fun declarationPath(parent: NodePath, declaration: KtDeclaration): NodePath =
        NodePath(parent.segments + segmentFor(declaration))

    private fun segmentFor(declaration: KtDeclaration): NodeSegment {
        val name = declaration.name ?: "<anonymous>"
        return when (declaration) {
            is KtClassOrObject -> NodeSegment.Class(name)
            is KtNamedFunction -> NodeSegment.Symbol(
                name,
                CallableDiscriminator(
                    arity = declaration.valueParameters.size,
                    parameterTypeTexts = declaration.valueParameters.map { it.typeReference?.text ?: "" },
                    receiverTypeText = declaration.receiverTypeReference?.text,
                ),
            )
            is KtProperty -> NodeSegment.Symbol(
                name,
                declaration.receiverTypeReference?.let {
                    CallableDiscriminator(arity = 0, parameterTypeTexts = emptyList(), receiverTypeText = it.text)
                },
            )
            else -> NodeSegment.Symbol(name, null)
        }
    }
}
