package pl.lukaszburzak.creye.orchestration.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import pl.lukaszburzak.creye.domain.change.ChangeComparison
import pl.lukaszburzak.creye.domain.change.ChangedFile
import pl.lukaszburzak.creye.domain.change.FileChangeState
import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.diagnostics.DiagnosticSource
import pl.lukaszburzak.creye.domain.diagnostics.Severity
import pl.lukaszburzak.creye.domain.diagnostics.SourceLocation
import java.nio.file.Files
import java.nio.file.Path

/**
 * ADR-003 git comparison stage: merge base of HEAD and the given branch compared
 * against the working directory (staged, unstaged, and untracked changes included),
 * normalized into [ChangedFile] records. No symbol detection happens here.
 *
 * Must run off the EDT; git commands are executed synchronously.
 */
class GitChangeComparator(private val project: Project) {

    fun compare(branchName: String): ChangeComparison {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        val repository = repositories.firstOrNull()
            ?: return failure("Project has no git repository")
        val diagnostics = mutableListOf<Diagnostic>()
        if (repositories.size > 1) {
            diagnostics += Diagnostic(
                DiagnosticSource.GIT, Severity.INFO,
                "Multiple git repositories found; comparing only ${repository.root.path}",
            )
        }
        val branch = repository.branches.findBranchByName(branchName)
            ?: return failure("Branch '$branchName' does not exist in the repository", diagnostics)
        val root = repository.root
        val mergeBase = GitHistoryUtils.getMergeBase(project, root, "HEAD", branch.fullName)
            ?: return failure("No merge base between HEAD and '$branchName'", diagnostics)
        val baseRev = mergeBase.rev

        val nameStatus = runGit(root, GitCommand.DIFF, "--name-status", "--find-renames", "-z", baseRev)
        if (!nameStatus.success()) {
            return failure("git diff --name-status failed: ${nameStatus.errorOutputAsJoinedString}", diagnostics)
        }
        val patchResult = runGit(root, GitCommand.DIFF, "-U0", "--find-renames", baseRev)
        if (!patchResult.success()) {
            return failure("git diff -U0 failed: ${patchResult.errorOutputAsJoinedString}", diagnostics)
        }
        val untracked = runGit(root, GitCommand.LS_FILES, "--others", "--exclude-standard", "-z")
        if (!untracked.success()) {
            return failure("git ls-files failed: ${untracked.errorOutputAsJoinedString}", diagnostics)
        }

        val entries = UnifiedDiffParser.parseNameStatusZ(nameStatus.output.joinToString("\n"))
        val patch = UnifiedDiffParser.parsePatch(patchResult.output.joinToString("\n"))
        val rootPath = Path.of(root.path)

        val files = mutableListOf<ChangedFile>()
        for (entry in entries) {
            val state = when (entry.status) {
                'A' -> FileChangeState.ADDED
                'M' -> FileChangeState.MODIFIED
                'D' -> FileChangeState.DELETED
                'R' -> FileChangeState.RENAMED
                else -> {
                    diagnostics += unsupported(entry.status, entry.path)
                    continue
                }
            }
            if (entry.path in patch.submodulePaths) {
                diagnostics += unsupported('S', entry.path)
                continue
            }
            files += changedFile(entry.path, entry.previousPath, state, patch, root, baseRev, rootPath, diagnostics)
                ?: continue
        }
        val tracked = files.mapTo(mutableSetOf()) { it.path }
        untracked.output.joinToString("\n").split('\u0000')
            .filter { it.isNotEmpty() && it !in tracked }
            .forEach { path ->
                files += changedFile(path, null, FileChangeState.ADDED, patch, root, baseRev, rootPath, diagnostics)
                    ?: return@forEach
            }
        return ChangeComparison(files, diagnostics)
    }

    private fun changedFile(
        path: String,
        previousPath: String?,
        state: FileChangeState,
        patch: DiffPatch,
        root: VirtualFile,
        baseRev: String,
        rootPath: Path,
        diagnostics: MutableList<Diagnostic>,
    ): ChangedFile? {
        val isKotlin = path.endsWith(".kt") || path.endsWith(".kts")
        var baseline: String? = null
        var current: String? = null
        if (isKotlin) {
            if (state != FileChangeState.ADDED) {
                baseline = try {
                    String(GitFileUtils.getFileContent(project, root, baseRev, previousPath ?: path), Charsets.UTF_8)
                } catch (e: Exception) {
                    diagnostics += Diagnostic(
                        DiagnosticSource.GIT, Severity.ERROR,
                        "Cannot read baseline content: ${e.message}", SourceLocation(path),
                    )
                    return null
                }
            }
            if (state != FileChangeState.DELETED) {
                current = try {
                    Files.readString(rootPath.resolve(path))
                } catch (e: Exception) {
                    diagnostics += Diagnostic(
                        DiagnosticSource.GIT, Severity.ERROR,
                        "Cannot read working-directory content: ${e.message}", SourceLocation(path),
                    )
                    return null
                }
            }
        }
        val hunks = when (state) {
            FileChangeState.MODIFIED, FileChangeState.RENAMED -> patch.hunksByPath[path].orEmpty()
            FileChangeState.ADDED, FileChangeState.DELETED -> emptyList() // whole-file change
        }
        return ChangedFile(path, previousPath, state, isKotlin, baseline, current, hunks)
    }

    private fun runGit(root: VirtualFile, command: GitCommand, vararg params: String): GitCommandResult {
        val handler = GitLineHandler(project, root, command)
        handler.addParameters(*params)
        handler.setSilent(true)
        return Git.getInstance().runCommand(handler)
    }

    private fun unsupported(status: Char, path: String) = Diagnostic(
        DiagnosticSource.GIT, Severity.WARNING,
        "Unsupported file state '$status'; file excluded from comparison", SourceLocation(path),
    )

    private fun failure(message: String, preceding: List<Diagnostic> = emptyList()) = ChangeComparison(
        emptyList(),
        preceding + Diagnostic(DiagnosticSource.GIT, Severity.ERROR, message),
    )
}
