# ADR-016 Diff Range Highlighting

## Problem & Context
- The combined diff shows a node's change set, but every changed line looks alike; there is no way to draw the eye to the code that a specific node (e.g. a clicked symbol) actually owns.
- This is the in-text sibling of ADR-015: gutter decoration marks the *margin*, range highlighting marks the *content* (line backgrounds, boxed blocks, side brackets) of a diff block.
- IntelliJ exposes content highlighting only through editor-level `RangeHighlighter`s carrying `TextAttributes` (or a `LineMarkerRenderer` / `CustomHighlighterRenderer`) in an `Editor`'s `MarkupModel`, the same primitive as ADR-015 but a different decoration payload.
- The data to drive it already exists from analysis: changed-symbol PSI ranges and the node-to-file mapping (ADR-005), so highlighting is a presentation concern over identities the plugin already mints, not new analysis.
- Diff editors carry pitfalls a naive highlight hits: the diff tool already paints add/delete backgrounds, the "after" editor is backed by the real working-dir document, and line numbers do not map uniformly across viewer types.

## Constraints
1. Range highlighting MUST appear only in the plugin's own combined diff view (ADR-015 constraint 1); it MUST NOT decorate diffs the plugin did not open, nor leak into the real file editor.
2. Each highlighted range MUST be correlated back to the structural node (ADR-005 `NodePath`) and/or change it originates from.
3. Highlighting logic MUST live in the ide / diff-presentation layer; it MUST NOT introduce git, PSI, or analysis data into the Compose rendering surface (ADR-011), nor bypass the ADR-001 layering.
4. Highlighting MUST degrade safely: a block whose viewer exposes no usable editor, or a range that cannot be mapped to the shown side, MUST be skipped rather than failing the view.
5. A highlight MUST remain visible over the diff tool's own line backgrounds.

## Decision
- Range highlighting MUST reuse the ADR-015 processor-scoped editor walk: it operates on the editors of blocks owned by the plugin's own `CombinedDiffComponentProcessor`, never through a global `com.intellij.diff.DiffExtension` (constraint 1).
- Highlights MUST be added through the editor-local markup model (`EditorEx.getMarkupModel()`), NOT the document markup model, so they cannot leak into the real working-dir file editor that the diff's "after" side shares (constraint 1).
- A highlight MUST be a `RangeHighlighter` over a line range, added with `HighlighterTargetArea.LINES_IN_RANGE`, and its decoration payload MUST be chosen by intent:
  - a `TextAttributes` background for emphasis fills,
  - a `TextAttributes` `EffectType.BOXED` / `ROUNDED_BOX` border for block outlines,
  - a `LineMarkerRenderer` for a margin-side bracket or stripe spanning the block.
  `CustomHighlighterRenderer` MAY be used only when none of the above expresses the intended decoration.
- Highlights MUST be placed at `HighlighterLayer.SELECTION` or above so they render over the diff tool's add/delete backgrounds (constraint 5).
- The highlighted side MUST be resolved per viewer: for a two-side viewer the working-dir ("after") editor MUST be used, whose document lines align with analysis ranges; for a unified viewer, ranges MUST be translated through the diff model before use, and skipped if translation is unavailable (constraint 4). Line ranges MUST be converted to offsets via the target editor's document.
- Each highlight MUST resolve its originating node by mapping the block's file path back to the `NodePath` whose `File` segment matches (constraint 2), consistent with ADR-015.
- All highlighter additions MUST run on the EDT and MUST be released with the diff dialog's `Disposable`, alongside the processor disposal already performed when the dialog closes.

## Rationale
- Reusing the ADR-015 processor walk keeps both decoration concerns on one scoped, registration-free mechanism; introducing a global `DiffExtension` here would re-incur the project-wide blast radius ADR-015 already rejected.
- Editor-local markup is the load-bearing choice: the two-side "after" editor is backed by the real file document, so document-level markup would bleed plugin highlights into ordinary editing. The editor-local model confines them to the diff view.
- Driving highlights from existing changed-symbol ranges and the node-to-file map (ADR-005) avoids a second source of truth for "which code belongs to this node", mirroring ADR-015's correlation rationale.
- Selecting the decoration primitive by intent (background vs box vs bracket) rather than fixing one keeps the record about *where and how highlights attach*, leaving the visual vocabulary to the interaction design, while still constraining layer, side, and markup model so any choice behaves correctly in a diff editor.
- Pinning the layer above the diff backgrounds and the side to the working-dir editor turns the two most common diff-highlighting failures (invisible fills, misaligned ranges) into explicit rules rather than rediscovered bugs.

## Notes
- This record decides the highlighting *mechanism* only. The concrete highlight vocabulary and triggers are deferred to a later record — candidates include highlighting the clicked symbol's body in its file block, tinting hunks that belong to a selected child node, and a transient "focus" highlight on navigation — pending the diff inspection interaction being settled, shared with ADR-015's deferred decoration vocabulary.
- ADR-015 (gutter) and this record (content) are deliberately split by decoration surface but share the same host, scoping, layering, and disposal rules; if a third diff-decoration concern appears, consider consolidating the shared rules into one record the others reference.
- The changed-symbol ranges this record highlights from must reach the ide layer. Inspection shows ADR-004 computes them in detection but the domain change model (`ChangedDeclaration`) drops them, so they are not reachable today. The resolution — retaining the current-side range on `ChangedDeclaration` and having the ide read the change set — is owned by ADR-019 and carried as a downstream-contract note on ADR-004; this record consumes the same ranges.
- The combined diff view itself is implemented in the ide layer but not yet captured by its own ADR (noted in ADR-015); both decoration records would reference it if it is recorded.
