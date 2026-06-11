# Shared type contracts for change-detection tasks

Every task implements against these signatures so tasks stay independent (PDR-001). Package root: `pl.lukaszburzak.creye.domain`. Domain types carry no IntelliJ/PSI/git4idea imports.

## `domain/change` — ADR-003 output

```kotlin
enum class FileChangeState { ADDED, MODIFIED, DELETED, RENAMED }
data class LineRange(val start: Int, val length: Int)             // 1-based; length 0 = empty side
data class Hunk(val baseline: LineRange, val current: LineRange)  // two-sided per ADR-003

data class ChangedFile(
    val path: String,             // repo-relative; for DELETED this is the baseline path
    val previousPath: String?,    // non-null only for RENAMED
    val state: FileChangeState,
    val isKotlin: Boolean,
    val baselineContent: String?, // null for ADDED; loaded only for Kotlin files
    val currentContent: String?,  // null for DELETED; loaded only for Kotlin files
    val hunks: List<Hunk>,        // empty for whole-file ADDED/DELETED and clean renames
)
data class ChangeComparison(val files: List<ChangedFile>, val diagnostics: List<Diagnostic>)
```

Notes:
- Hunks are line-based (verbatim from `git diff -U0` `@@` headers); detection converts to text offsets internally.
- Unsupported file states (ADR-003) are defined operationally as: typechange (`T`), unmerged (`U`), and submodule entries — excluded with a `git` diagnostic.
- Untracked files count as `ADDED` (ADR-003 constraint 3 covers files never added to the index).

## `domain/change` — ADR-004 output

```kotlin
enum class ChangeKind { ADDED, MODIFIED, DELETED }
data class ChangedDeclaration(
    val identity: NodePath, val kind: ChangeKind,
    val filePath: String, val displayName: String,
)
data class ContextualDeclaration(val identity: NodePath, val filePath: String)
data class FileMove(val previousPath: String, val path: String)   // rename without content change
data class ChangedSymbols(
    val changed: List<ChangedDeclaration>,
    val contextual: List<ContextualDeclaration>,
    val movedFiles: List<FileMove>,
    val diagnostics: List<Diagnostic>,
)
```

## `domain/identity` — ADR-005

```kotlin
data class NodePath(val segments: List<NodeSegment>)   // value equality = identity
sealed interface NodeSegment {
    data class Module(val id: String) : NodeSegment
    data class Package(val fqName: String) : NodeSegment          // "<default>" sentinel
    data class File(val name: String, val moduleRelativePath: String) : NodeSegment
    data class Class(val name: String) : NodeSegment
    data class Symbol(val name: String, val discriminator: CallableDiscriminator?) : NodeSegment
}
data class CallableDiscriminator(
    val arity: Int, val parameterTypeTexts: List<String>, val receiverTypeText: String?,
)
```

File segment uses module-relative path (permitted by ADR-005) so this milestone needs no source-set discovery.

## `domain/diagnostics` — ADR-010

```kotlin
enum class DiagnosticSource { GIT, PROJECT_MODEL, CHANGED_SYMBOL_DETECTION,
                              DEPENDENCY_RESOLUTION, GRAPH_CONSTRUCTION, RENDERING }
enum class Severity { ERROR, WARNING, INFO }
data class SourceLocation(val filePath: String, val line: Int? = null)
data class Diagnostic(
    val source: DiagnosticSource, val severity: Severity, val message: String,
    val location: SourceLocation? = null,
    // `attachment` (ADR-007 key) is added in the dependency-model milestone; optional per ADR-010
)
```

## Service aggregate (pipeline wiring)

```kotlin
data class ChangeDetection(
    val comparison: ChangeComparison,
    val symbols: ChangedSymbols,
)   // GraphAnalysisService.detectChanges(branch: String): ChangeDetection
```
