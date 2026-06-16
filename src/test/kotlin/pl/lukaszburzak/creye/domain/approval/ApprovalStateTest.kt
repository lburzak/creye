package pl.lukaszburzak.creye.domain.approval

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.change.FileMove
import pl.lukaszburzak.creye.domain.change.SourceRange
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment
import pl.lukaszburzak.creye.ide.ApprovalPersistence

class ApprovalStateTest {

    private val module = NodePath(listOf(NodeSegment.Module("app")))
    private val pkg = module.child(NodeSegment.Package("demo"))
    private val file = pkg.child(NodeSegment.File("A.kt", "src/A.kt"))
    private val clazz = file.child(NodeSegment.Class("Box"))
    private val grow = clazz.child(NodeSegment.Symbol("grow", null))
    private val shrink = clazz.child(NodeSegment.Symbol("shrink", null))

    @Test
    fun `key serialization is stable segment-value format`() {
        assertEquals(
            "M:3:app|P:4:demo|F:4:A.kt:8:src/A.kt|C:3:Box|S:4:grow:_",
            approvalKey(grow),
        )
    }

    @Test
    fun `leaf approval toggles on and off`() {
        val symbols = symbols(declaration(grow, before = "fun grow() = 1", after = "fun grow() = 2"))

        val approved = ApprovalState().toggle(grow, symbols)
        assertTrue(approved.isApproved(symbols.changed.single()))

        val revoked = approved.toggle(grow, symbols)
        assertFalse(revoked.isApproved(symbols.changed.single()))
    }

    @Test
    fun `container summary derives none partial and full from changed descendants`() {
        val symbols = symbols(
            declaration(grow, before = "fun grow() = 1", after = "fun grow() = 2"),
            declaration(shrink, before = "fun shrink() = 1", after = "fun shrink() = 2"),
        )

        val none = ApprovalState()
        assertEquals(ApprovalCompleteness.NONE, none.summary(clazz, symbols)?.completeness)

        val partial = none.toggle(grow, symbols)
        assertEquals(ApprovalCompleteness.PARTIAL, partial.summary(clazz, symbols)?.completeness)

        val full = partial.toggle(clazz, symbols)
        assertEquals(ApprovalCompleteness.FULL, full.summary(clazz, symbols)?.completeness)
    }

    @Test
    fun `container with no changed descendants has no approval state`() {
        assertNull(ApprovalState().summary(clazz, symbols()))
    }

    @Test
    fun `pruning retains matching fingerprints and drops changed fingerprints`() {
        val original = symbols(declaration(grow, before = "fun grow() = 1", after = "fun grow() = 2"))
        val approved = ApprovalState().toggle(grow, original)

        assertTrue(approved.pruneTo(original).isApproved(original.changed.single()))

        val changed = symbols(declaration(grow, before = "fun grow() = 1", after = "fun grow() = 3"))
        assertFalse(approved.pruneTo(changed).isApproved(changed.changed.single()))
    }

    @Test
    fun `deletion approval fingerprints baseline side content`() {
        val deletion = symbols(
            declaration(grow, kind = ChangeKind.DELETED, before = "fun grow() = 1", after = null),
        )

        val approved = ApprovalState().toggle(grow, deletion)

        assertTrue(approved.isApproved(deletion.changed.single()))
    }

    @Test
    fun `toggle ignores targets outside the changed structural set`() {
        val symbols = symbols(declaration(grow, before = "fun grow() = 1", after = "fun grow() = 2"))
        val externalLike = module.child(NodeSegment.Package("external")).child(NodeSegment.File("B.kt", "B.kt"))

        assertTrue(ApprovalState().toggle(externalLike, symbols).entries.isEmpty())
    }

    @Test
    fun `pruning migrates moved file keys only when fingerprint matches`() {
        val oldFile = pkg.child(NodeSegment.File("A.kt", "old/A.kt"))
        val oldGrow = oldFile.child(NodeSegment.Class("Box")).child(NodeSegment.Symbol("grow", null))
        val oldDeclaration = declaration(oldGrow, filePath = "old/A.kt", before = "fun grow() = 1", after = "fun grow() = 2")
        val newDeclaration = declaration(grow, filePath = "src/A.kt", before = "fun grow() = 1", after = "fun grow() = 2")
        val approved = ApprovalState().toggle(oldGrow, symbols(oldDeclaration))

        val pruned = approved.pruneTo(
            symbols(newDeclaration, moves = listOf(FileMove(previousPath = "old/A.kt", path = "src/A.kt"))),
        )

        assertFalse(approvalKey(oldGrow) in pruned.entries)
        assertTrue(approvalKey(grow) in pruned.entries)
        assertTrue(pruned.isApproved(newDeclaration))
    }

    @Test
    fun `persistence round trip preserves approval entries`() {
        val symbols = symbols(declaration(grow, before = "fun grow() = 1", after = "fun grow() = 2"))
        val approved = ApprovalState().toggle(grow, symbols)
        val first = ApprovalPersistence()
        first.replace(approved)

        val second = ApprovalPersistence()
        second.loadState(first.getState())

        assertEquals(approved, second.snapshot())
    }

    private fun declaration(
        path: NodePath,
        kind: ChangeKind = ChangeKind.MODIFIED,
        filePath: String = "src/A.kt",
        before: String?,
        after: String?,
    ) = ChangedDeclaration(
        identity = path,
        kind = kind,
        filePath = filePath,
        displayName = path.segments.last().let {
            when (it) {
                is NodeSegment.Symbol -> it.name
                is NodeSegment.Class -> it.name
                is NodeSegment.File -> it.name
                is NodeSegment.Module -> it.id
                is NodeSegment.Package -> it.fqName
            }
        },
        currentRange = after?.let { SourceRange(0, it.length, 1, 1) },
        baselineRange = before?.let { SourceRange(0, it.length, 1, 1) },
        currentText = after,
        baselineText = before,
    )

    private fun symbols(
        vararg declarations: ChangedDeclaration,
        moves: List<FileMove> = emptyList(),
    ) = ChangedSymbols(
        changed = declarations.toList(),
        contextual = emptyList(),
        movedFiles = moves,
        diagnostics = emptyList(),
    )

    private fun NodePath.child(segment: NodeSegment): NodePath = NodePath(segments + segment)
}
