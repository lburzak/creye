package pl.lukaszburzak.creye.domain.change

import pl.lukaszburzak.creye.domain.diagnostics.Diagnostic

enum class FileChangeState { ADDED, MODIFIED, DELETED, RENAMED }

/** 1-based line range; length 0 marks an empty side of a hunk. */
data class LineRange(val start: Int, val length: Int)

/** Two-sided changed range (ADR-003): baseline-side removal and current-side addition. */
data class Hunk(val baseline: LineRange, val current: LineRange)

/**
 * Normalized git comparison record (ADR-003). Hunk ranges are line-based, verbatim from
 * diff output; changed symbol detection converts them to text offsets internally.
 * Content is loaded only for Kotlin files.
 */
data class ChangedFile(
    /** Repo-relative; for [FileChangeState.DELETED] this is the baseline path. */
    val path: String,
    /** Non-null only for [FileChangeState.RENAMED]. */
    val previousPath: String?,
    val state: FileChangeState,
    val isKotlin: Boolean,
    /** Null for [FileChangeState.ADDED]. */
    val baselineContent: String?,
    /** Null for [FileChangeState.DELETED]. */
    val currentContent: String?,
    /** Empty for whole-file ADDED/DELETED and for clean renames. */
    val hunks: List<Hunk>,
)

data class ChangeComparison(
    val files: List<ChangedFile>,
    val diagnostics: List<Diagnostic>,
)
