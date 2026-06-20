package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
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

/** A Gradle module resolved to its ADR-005 container id and optional source-set name. */
data class GradleModule(val containerId: String, val sourceSet: String?)

/**
 * Resolves the ADR-005 module segment for a repo-relative path from the IntelliJ
 * project model. For deleted files the VirtualFile no longer exists on disk, so the
 * fallback uses source-root containment: the project model retains module source roots
 * even after files are removed, so the deepest matching root wins.
 */
class ProjectFileSegments(
    private val project: Project,
    private val repositoryRootPath: String,
    private val gradleModule: (Module) -> GradleModule? = ::defaultGradleModule,
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
    private val unsyncedSubprojectDir: (VirtualFile, Module) -> String? = ::defaultUnsyncedSubprojectDir,
) : (String) -> FileSegmentContext {

    override fun invoke(path: String): FileSegmentContext {
        val virtualFile = fileForPath(path)
        val module = if (virtualFile != null) {
            moduleForFile(virtualFile) ?: return FileSegmentContext(
                UNRESOLVED_MODULE,
                path,
                diagnostics = listOf(projectModelDiagnostic(path, "Module ownership could not be determined; using unresolved module")),
            )
        } else {
            // Deleted file: resolve module by source-root containment from the comparison target branch's project model.
            moduleForDeletedPath("$repositoryRootPath/$path") ?: return FileSegmentContext(
                UNRESOLVED_MODULE,
                path,
                diagnostics = listOf(projectModelDiagnostic(path, "File ownership could not be determined; using unresolved module")),
            )
        }
        val gradle = gradleModule(module)
        if (gradle != null) {
            val unsyncedDir = virtualFile?.let { unsyncedSubprojectDir(it, module) }
            val diagnostics = if (unsyncedDir != null) {
                listOf(projectModelDiagnostic(
                    path,
                    "'$unsyncedDir' has a Gradle build script but is not an imported module, so its files are " +
                        "attributed to '${gradle.containerId}'; re-sync Gradle to graph it as its own module.",
                ))
            } else {
                emptyList()
            }
            return FileSegmentContext(gradle.containerId, path, gradle.sourceSet, diagnostics)
        }
        return FileSegmentContext(
            module.name,
            path,
            diagnostics = listOf(projectModelDiagnostic(path, "Gradle module id was not available for '${module.name}'; using IntelliJ module name")),
        )
    }

    companion object {
        const val UNRESOLVED_MODULE = "<unresolved-module>"
        const val GRADLE_SYSTEM_ID = "GRADLE"

        /** External-module-type tag Gradle stamps on per-source-set modules (avoids a Gradle-plugin dependency). */
        private const val SOURCE_SET_MODULE_TYPE = "sourceSet"

        /**
         * Resolves a Gradle module to its container id and source set. Gradle's external project id
         * for a per-source-set module is `<projectPath>:<sourceSet>` (e.g. `:feature:channels-list:main`),
         * but a holder module (e.g. `:feature:channels-list`, owning `build.gradle.kts`) shares that
         * shape with a nested project path — so the last `:` token is split off only when the project
         * model confirms this is a source-set module. Holder and fallback modules keep their full id
         * and report no source set.
         */
        private fun defaultGradleModule(module: Module): GradleModule? {
            if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, module)) return null
            val id = ExternalSystemApiUtil.getExternalProjectId(module)?.takeIf { it.isNotBlank() } ?: return null
            val moduleType = ExternalSystemModulePropertyManager.getInstance(module).getExternalModuleType()
            if (moduleType == SOURCE_SET_MODULE_TYPE) {
                val separator = id.lastIndexOf(':')
                if (separator > 0 && separator < id.length - 1) {
                    return GradleModule(id.substring(0, separator), id.substring(separator + 1))
                }
            }
            return GradleModule(id, null)
        }

        private const val SETTINGS_GRADLE_KTS = "settings.gradle.kts"
        private const val SETTINGS_GRADLE = "settings.gradle"

        /**
         * Detects a file that lands on a parent container module because an intermediate directory
         * holds a Gradle build script yet was never imported as a module (typically a subproject
         * added without a Gradle re-sync). Walks from the file up to the resolved module's project
         * directory; the deepest intermediate directory carrying `build.gradle(.kts)` — but not a
         * settings script, which marks the build root rather than a subproject — is the culprit.
         * Returns its path, or null when the file genuinely belongs to its resolved module.
         */
        private fun defaultUnsyncedSubprojectDir(file: VirtualFile, module: Module): String? {
            val moduleDir = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
            var dir = file.parent
            while (dir != null && dir.path != moduleDir && dir.path.startsWith("$moduleDir/")) {
                val hasBuildScript = dir.findChild("build.gradle.kts") != null || dir.findChild("build.gradle") != null
                val isBuildRoot = dir.findChild(SETTINGS_GRADLE_KTS) != null || dir.findChild(SETTINGS_GRADLE) != null
                if (hasBuildScript && !isBuildRoot) return dir.path
                dir = dir.parent
            }
            return null
        }

        private fun projectModelDiagnostic(path: String, message: String) = Diagnostic(
            source = DiagnosticSource.PROJECT_MODEL,
            severity = Severity.WARNING,
            message = message,
            location = SourceLocation(path),
        )
    }
}
