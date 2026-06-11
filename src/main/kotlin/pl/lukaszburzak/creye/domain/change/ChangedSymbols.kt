package pl.lukaszburzak.creye.domain.change

import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic
import pl.lukaszburzak.creye.domain.identity.NodePath

enum class ChangeKind { ADDED, MODIFIED, DELETED }

/** A declaration whose own range intersects a changed hunk (ADR-004). */
data class ChangedDeclaration(
    val identity: NodePath,
    val kind: ChangeKind,
    val filePath: String,
    val displayName: String,
)

/** A declaration a hunk touches without changing its own range (ADR-004 constraint 4). */
data class ContextualDeclaration(
    val identity: NodePath,
    val filePath: String,
)

/** Rename without content change: file moved, no declaration change (ADR-004). */
data class FileMove(val previousPath: String, val path: String)

data class ChangedSymbols(
    val changed: List<ChangedDeclaration>,
    val contextual: List<ContextualDeclaration>,
    val movedFiles: List<FileMove>,
    val diagnostics: List<Diagnostic>,
)
