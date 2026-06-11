package pl.lukaszburzak.creye.orchestration.git

import pl.lukaszburzak.creye.domain.change.Hunk
import pl.lukaszburzak.creye.domain.change.LineRange

/** One `--name-status -z` entry; [status] is the leading letter (`R100` → `R`). */
data class NameStatusEntry(val status: Char, val path: String, val previousPath: String?)

/**
 * Parsed `-U0` patch. Hunks are keyed by current path, or baseline path for deletions.
 * [submodulePaths] collects sections whose body is a `Subproject commit` pointer change.
 */
data class DiffPatch(
    val hunksByPath: Map<String, List<Hunk>>,
    val submodulePaths: Set<String>,
)

/** Pure parser of git diff output (ADR-003); knows nothing of git execution or PSI. */
object UnifiedDiffParser {

    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")

    fun parseNameStatusZ(output: String): List<NameStatusEntry> {
        val tokens = output.split('\u0000').filter { it.isNotEmpty() }
        val entries = mutableListOf<NameStatusEntry>()
        var i = 0
        while (i < tokens.size) {
            val status = tokens[i].first()
            if (status == 'R' || status == 'C') {
                if (i + 2 > tokens.lastIndex) break
                entries += NameStatusEntry(status, path = tokens[i + 2], previousPath = tokens[i + 1])
                i += 3
            } else {
                if (i + 1 > tokens.lastIndex) break
                entries += NameStatusEntry(status, path = tokens[i + 1], previousPath = null)
                i += 2
            }
        }
        return entries
    }

    fun parsePatch(patch: String): DiffPatch {
        val hunks = mutableMapOf<String, MutableList<Hunk>>()
        val submodules = mutableSetOf<String>()
        var oldPath: String? = null
        var newPath: String? = null
        // Lines of the current hunk body left to consume; body lines are never headers.
        var pendingBody = 0
        for (line in patch.lineSequence()) {
            if (pendingBody > 0) {
                if (line.startsWith("\\")) continue // "\ No newline at end of file" is extra
                if (line.startsWith("+Subproject commit") || line.startsWith("-Subproject commit")) {
                    (newPath ?: oldPath)?.let { submodules += it }
                }
                pendingBody--
                continue
            }
            when {
                line.startsWith("diff --git ") -> {
                    oldPath = null
                    newPath = null
                }
                line.startsWith("--- ") -> oldPath = stripPathPrefix(line.removePrefix("--- "))
                line.startsWith("+++ ") -> newPath = stripPathPrefix(line.removePrefix("+++ "))
                line.startsWith("@@ ") -> {
                    val match = HUNK_HEADER.find(line) ?: continue
                    val key = newPath ?: oldPath ?: continue
                    val baseline = LineRange(
                        start = match.groupValues[1].toInt(),
                        length = match.groupValues[2].ifEmpty { "1" }.toInt(),
                    )
                    val current = LineRange(
                        start = match.groupValues[3].toInt(),
                        length = match.groupValues[4].ifEmpty { "1" }.toInt(),
                    )
                    hunks.getOrPut(key) { mutableListOf() } += Hunk(baseline, current)
                    pendingBody = baseline.length + current.length
                }
            }
        }
        return DiffPatch(hunks, submodules)
    }

    private fun stripPathPrefix(raw: String): String? {
        if (raw == "/dev/null") return null
        return raw.removePrefix("a/").removePrefix("b/")
    }
}
