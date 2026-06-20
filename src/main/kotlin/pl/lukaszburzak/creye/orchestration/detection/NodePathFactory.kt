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

/**
 * Module context for the file segment; supplied by the caller (ADR-002 seam).
 *
 * [moduleId] is the module *container* id with any source-set suffix already removed.
 * [sourceSet] is the resolved source-set name (e.g. `main`, `test`) or null when the
 * file's module is not a per-source-set module (a build-script holder module, or the
 * IntelliJ-module-name fallback). The caller resolves the source set from the project
 * model, so the path factory never has to guess it from the id string.
 */
data class FileSegmentContext(
    val moduleId: String,
    val moduleRelativePath: String,
    val sourceSet: String? = null,
    val diagnostics: List<Diagnostic> = emptyList(),
)

/**
 * Mints ADR-005 structural-path identity from PSI text only — no resolved types,
 * no fully-qualified names beyond the declared package.
 */
object NodePathFactory {

    /**
     * Builds the ADR-005 path head: module container, optional source set, optional package,
     * then file. The source set is omitted when the module is not a per-source-set module. The
     * package is omitted for Kotlin scripts (`build.gradle.kts` and friends), which are not source
     * files in a package-rooted source set and so attach directly under their module container.
     */
    fun filePath(file: KtFile, context: FileSegmentContext, fileName: String): NodePath {
        val segments = buildList {
            add(NodeSegment.Module(context.moduleId))
            context.sourceSet?.let { add(NodeSegment.SourceSet(it)) }
            if (!file.isScript()) {
                val packageName = file.packageFqName.asString().ifEmpty { NodeSegment.DEFAULT_PACKAGE }
                add(NodeSegment.Package(packageName))
            }
            add(NodeSegment.File(fileName, context.moduleRelativePath))
        }
        return NodePath(segments)
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
