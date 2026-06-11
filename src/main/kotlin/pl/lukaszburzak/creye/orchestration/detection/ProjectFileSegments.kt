package pl.lukaszburzak.creye.orchestration.detection

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Resolves the ADR-005 module segment for a repo-relative path from the IntelliJ
 * project model. Deleted files have no VirtualFile; they (and files outside any
 * module) fall back to a sentinel module so identity stays deterministic.
 */
class ProjectFileSegments(
    private val project: Project,
    private val repositoryRootPath: String,
) : (String) -> FileSegmentContext {

    override fun invoke(path: String): FileSegmentContext {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath("$repositoryRootPath/$path")
            ?: return FileSegmentContext(UNRESOLVED_MODULE, path)
        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
            ?: return FileSegmentContext(UNRESOLVED_MODULE, path)
        return FileSegmentContext(module.name, path)
    }

    companion object {
        const val UNRESOLVED_MODULE = "<unresolved-module>"
    }
}
