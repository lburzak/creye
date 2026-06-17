package pl.lukaszburzak.creye.domain.approval

import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.change.FileMove
import pl.lukaszburzak.creye.domain.identity.CallableDiscriminator
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.domain.identity.fileSegment
import pl.lukaszburzak.creye.domain.identity.isDescendantOf
import java.security.MessageDigest

const val APPROVAL_FINGERPRINT_SCHEME_VERSION: Int = 1

data class ApprovalEntry(
    val key: String,
    val fingerprint: String,
    val schemeVersion: Int = APPROVAL_FINGERPRINT_SCHEME_VERSION,
)

enum class ApprovalCompleteness {
    FULL,
    PARTIAL,
    NONE,
}

data class ApprovalSummary(
    val approvedLeaves: Int,
    val totalLeaves: Int,
) {
    init {
        require(totalLeaves >= 0) { "totalLeaves must be non-negative" }
        require(approvedLeaves in 0..totalLeaves) { "approvedLeaves must be in 0..totalLeaves" }
    }

    val completeness: ApprovalCompleteness? =
        when {
            totalLeaves == 0 -> null
            approvedLeaves == totalLeaves -> ApprovalCompleteness.FULL
            approvedLeaves == 0 -> ApprovalCompleteness.NONE
            else -> ApprovalCompleteness.PARTIAL
        }

    val isFullyApproved: Boolean get() = completeness == ApprovalCompleteness.FULL
}

data class ApprovalState(
    val entries: Map<String, ApprovalEntry> = emptyMap(),
) {

    fun pruneTo(symbols: ChangedSymbols): ApprovalState {
        val live = linkedMapOf<String, ApprovalEntry>()
        for (declaration in symbols.changed) {
            val key = approvalKey(declaration.identity)
            val fingerprint = declaration.approvalFingerprint()
            val exact = entries[key]
            if (exact != null && exact.matches(key, fingerprint)) {
                live[key] = exact
                continue
            }

            val moved = relocatedKeys(declaration, symbols.movedFiles)
                .firstNotNullOfOrNull { oldKey ->
                    entries[oldKey]?.takeIf { it.matches(oldKey, fingerprint) }
                }
            if (moved != null) {
                live[key] = moved.copy(key = key)
            }
        }
        return copy(entries = live)
    }

    fun toggle(target: NodePath, symbols: ChangedSymbols): ApprovalState {
        val affected = symbols.approvalDeclarations(target)
        if (affected.isEmpty()) return this

        val fullyApproved = affected.all(::isApproved)
        val next = entries.toMutableMap()
        if (fullyApproved) {
            affected.forEach { next.remove(approvalKey(it.identity)) }
        } else {
            affected.forEach { declaration ->
                val key = approvalKey(declaration.identity)
                next[key] = ApprovalEntry(key, declaration.approvalFingerprint())
            }
        }
        return copy(entries = next)
    }

    fun summary(target: NodePath, symbols: ChangedSymbols): ApprovalSummary? {
        val affected = symbols.approvalDeclarations(target)
        if (affected.isEmpty()) return null
        return ApprovalSummary(
            approvedLeaves = affected.count(::isApproved),
            totalLeaves = affected.size,
        )
    }

    fun isApproved(declaration: ChangedDeclaration): Boolean {
        val key = approvalKey(declaration.identity)
        return entries[key].matches(key, declaration.approvalFingerprint())
    }

    private fun ApprovalEntry?.matches(key: String, fingerprint: String): Boolean =
        this != null &&
            this.key == key &&
            this.fingerprint == fingerprint &&
            this.schemeVersion == APPROVAL_FINGERPRINT_SCHEME_VERSION
}

fun ChangedSymbols.approvalDeclarations(target: NodePath): List<ChangedDeclaration> =
    changed.filter { it.identity == target || it.identity.isDescendantOf(target) }

fun ChangedDeclaration.approvalFingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updatePart(kind.name)
    digest.updatePart(baselineText.orEmpty())
    digest.updatePart(currentText.orEmpty())
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}

fun approvalKey(path: NodePath): String =
    path.segments.joinToString(separator = "|") { pathSegment ->
        when (pathSegment) {
            is NodeSegment.Module -> segment("M", pathSegment.id)
            is NodeSegment.SourceSet -> segment("SS", pathSegment.name)
            is NodeSegment.Package -> segment("P", pathSegment.fqName)
            is NodeSegment.File -> segment("F", pathSegment.name, pathSegment.moduleRelativePath)
            is NodeSegment.Class -> segment("C", pathSegment.name)
            is NodeSegment.Symbol -> segment("S", pathSegment.name, pathSegment.discriminator?.stableValue())
        }
    }

private fun relocatedKeys(declaration: ChangedDeclaration, moves: List<FileMove>): Sequence<String> = sequence {
    val file = declaration.identity.fileSegment() ?: return@sequence
    for (move in moves) {
        if (declaration.filePath != move.path && file.moduleRelativePath != move.path) continue
        declaration.identity.withFilePath(move.previousPath)?.let { yield(approvalKey(it)) }
    }
}

private fun NodePath.withFilePath(path: String): NodePath? {
    val index = segments.indexOfFirst { it is NodeSegment.File }
    if (index < 0) return null
    val next = segments.toMutableList()
    next[index] = NodeSegment.File(name = path.substringAfterLast('/'), moduleRelativePath = path)
    return NodePath(next)
}

private fun CallableDiscriminator.stableValue(): String =
    buildString {
        append("arity=")
        append(arity)
        append(";receiver=")
        append(receiverTypeText.orEmpty())
        append(";params=")
        parameterTypeTexts.forEachIndexed { index, text ->
            if (index > 0) append(",")
            append(lengthPrefixed(text))
        }
    }

private fun segment(tag: String, vararg values: String?): String =
    buildString {
        append(tag)
        values.forEach { value ->
            append(":")
            append(value?.let(::lengthPrefixed) ?: "_")
        }
    }

private fun lengthPrefixed(value: String): String =
    "${value.length}:$value"

private fun MessageDigest.updatePart(value: String) {
    update(value.length.toString().toByteArray(Charsets.UTF_8))
    update(0)
    update(value.toByteArray(Charsets.UTF_8))
    update(0)
}
