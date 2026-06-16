package pl.lukaszburzak.creye.ide

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import pl.lukaszburzak.creye.domain.approval.ApprovalState
import pl.lukaszburzak.creye.domain.change.ChangeKind
import pl.lukaszburzak.creye.domain.change.ChangedDeclaration
import pl.lukaszburzak.creye.domain.change.ChangedSymbols
import pl.lukaszburzak.creye.domain.change.SourceRange
import pl.lukaszburzak.creye.domain.identity.NodePath
import pl.lukaszburzak.creye.domain.identity.NodeSegment

class NodeDiffPresenterTest {

    private val module = NodePath(listOf(NodeSegment.Module("app")))
    private val pkg = module.child(NodeSegment.Package("demo"))
    private val file = pkg.child(NodeSegment.File("A.kt", "src/A.kt"))
    private val clazz = file.child(NodeSegment.Class("Box"))
    private val grow = clazz.child(NodeSegment.Symbol("grow", null))
    private val shrink = clazz.child(NodeSegment.Symbol("shrink", null))

    @Test
    fun `approval decorations render gutter for every supported leaf and highlight approved ranges`() {
        val symbols = symbols(
            declaration(grow, line = 10),
            declaration(shrink, line = 14),
        )
        val approvals = ApprovalState().toggle(grow, symbols)

        val decorations = NodeDiffPresenter.approvalDecorations(symbols, approvals)

        assertEquals(
            setOf(
                ApprovalDiffDecoration.Gutter(grow, "src/A.kt", line = 10, approved = true),
                ApprovalDiffDecoration.Highlight(grow, "src/A.kt", startLine = 10, endLine = 12),
                ApprovalDiffDecoration.Gutter(shrink, "src/A.kt", line = 14, approved = false),
            ),
            decorations.toSet(),
        )
    }

    @Test
    fun `approval decorations skip declarations without current-side ranges`() {
        val symbols = symbols(
            ChangedDeclaration(
                identity = grow,
                kind = ChangeKind.DELETED,
                filePath = "src/A.kt",
                displayName = "grow",
                baselineRange = SourceRange(0, 10, 10, 12),
                baselineText = "fun grow() = 1",
            ),
        )

        assertTrue(NodeDiffPresenter.approvalDecorations(symbols, ApprovalState()).isEmpty())
    }

    @Test
    fun `approval decorations include file-level gutter controls`() {
        val symbols = symbols(declaration(grow, line = 10))
        val approvals = ApprovalState().toggle(file, symbols)

        val decorations = NodeDiffPresenter.approvalDecorations(symbols, approvals, fileTargets = listOf(file))

        assertTrue(
            decorations.contains(
                ApprovalDiffDecoration.Gutter(file, "src/A.kt", line = 1, approved = true),
            ),
        )
    }

    private fun declaration(path: NodePath, line: Int) =
        ChangedDeclaration(
            identity = path,
            kind = ChangeKind.MODIFIED,
            filePath = "src/A.kt",
            displayName = path.segments.last().let { (it as NodeSegment.Symbol).name },
            currentRange = SourceRange(0, 10, line, line + 2),
            baselineRange = SourceRange(0, 10, line, line + 2),
            currentText = "${path.segments.last()} after",
            baselineText = "${path.segments.last()} before",
        )

    private fun symbols(vararg declarations: ChangedDeclaration) =
        ChangedSymbols(
            changed = declarations.toList(),
            contextual = emptyList(),
            movedFiles = emptyList(),
            diagnostics = emptyList(),
        )

    private fun NodePath.child(segment: NodeSegment): NodePath = NodePath(segments + segment)
}
