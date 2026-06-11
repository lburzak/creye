package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.diagnostics.SourceLocation

/**
 * Resolves the ADR-005 module segment for a repo-relative path from the IntelliJ
 * project model. Deleted files have no VirtualFile; they (and files outside any
 * module) fall back to a sentinel module so identity stays deterministic.
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
) : (String) -> FileSegmentContext {

    override fun invoke(path: String): FileSegmentContext {
        val virtualFile = fileForPath(path)
            ?: return FileSegmentContext(
                UNRESOLVED_MODULE,
                path,
                listOf(projectModelDiagnostic(path, "File ownership could not be determined; using unresolved module")),
            )
        val module = moduleForFile(virtualFile)
            ?: return FileSegmentContext(
                UNRESOLVED_MODULE,
                path,
                listOf(projectModelDiagnostic(path, "Module ownership could not be determined; using unresolved module")),
            )
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
