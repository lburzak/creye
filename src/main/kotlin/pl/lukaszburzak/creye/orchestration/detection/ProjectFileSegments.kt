package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.diagnostics.SourceLocation

/**
 * Resolves the ADR-005 module segment for a repo-relative path from the IntelliJ
 * project model. For deleted files the VirtualFile no longer exists on disk, so the
 * fallback uses source-root containment: the project model retains module source roots
 * even after files are removed, so the deepest matching root wins.
 */
class ProjectFileSegments(
    private val project: Project,
    private val repositoryRootPath: String,
    private val gradleModuleId: (Module) -> String? = ::defaultGradleModuleId,
    private val fileForPath: (String) -> VirtualFile? = { path ->
        LocalFileSystem.getInstance().findFileByPath("$repositoryRootPath/$path")
    },
    private val moduleForFile: (VirtualFile) -> Module? = { file ->
        ModuleUtilCore.findModuleForFile(file, project)
    },
    private val moduleForDeletedPath: (String) -> Module? = { absolutePath ->
        ModuleManager.getInstance(project).modules
            .flatMap { module -> ModuleRootManager.getInstance(module).sourceRoots.map { it.path to module } }
            .filter { (rootPath, _) -> absolutePath.startsWith("$rootPath/") }
            .maxByOrNull { (rootPath, _) -> rootPath.length }
            ?.second
    },
) : (String) -> FileSegmentContext {

    override fun invoke(path: String): FileSegmentContext {
        val virtualFile = fileForPath(path)
        val module = if (virtualFile != null) {
            moduleForFile(virtualFile) ?: return FileSegmentContext(
                UNRESOLVED_MODULE,
                path,
                listOf(projectModelDiagnostic(path, "Module ownership could not be determined; using unresolved module")),
            )
        } else {
            // Deleted file: resolve module by source-root containment from the comparison target branch's project model.
            moduleForDeletedPath("$repositoryRootPath/$path") ?: return FileSegmentContext(
                UNRESOLVED_MODULE,
                path,
                listOf(projectModelDiagnostic(path, "File ownership could not be determined; using unresolved module")),
            )
        }
        val gradleId = gradleModuleId(module)
        if (gradleId != null) return FileSegmentContext(gradleId, path)
        return FileSegmentContext(
            module.name,
            path,
            listOf(projectModelDiagnostic(path, "Gradle module id was not available for '${module.name}'; using IntelliJ module name")),
        )
    }

    companion object {
        const val UNRESOLVED_MODULE = "<unresolved-module>"
        const val GRADLE_SYSTEM_ID = "GRADLE"

        private fun defaultGradleModuleId(module: Module): String? {
            if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, module)) return null
            return ExternalSystemApiUtil.getExternalProjectId(module)?.takeIf { it.isNotBlank() }
        }

        private fun projectModelDiagnostic(path: String, message: String) = Diagnostic(
            source = DiagnosticSource.PROJECT_MODEL,
            severity = Severity.WARNING,
            message = message,
            location = SourceLocation(path),
        )
    }
}
