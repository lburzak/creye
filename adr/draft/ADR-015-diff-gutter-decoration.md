# ADR-015 Diff Gutter Decoration

## Problem & Context
- There is a need for improving diff UX with some icons

## Constraints
1. Gutter decoration MUST appear only in the plugin's own combined diff view; it MUST NOT decorate diffs the plugin did not open.
2. Each decorated block MUST be correlated back to the structural node (ADR-005 `NodePath`) and/or change it originates from.
3. Decoration logic MUST live in the ide / diff-presentation layer; it MUST NOT introduce git, PSI, or analysis data into the Compose rendering surface (ADR-011), nor bypass the ADR-001 layering.
4. Decoration MUST degrade safely: if a diff block exposes no text editor (binary, unsupported viewer), the affordance MUST be skipped rather than failing the view.

## Decision
- The combined diff MUST be decorated by walking the editors of the blocks owned by the plugin's own `CombinedDiffComponentProcessor`, after `setBlocks`, rather than by registering a global `com.intellij.diff.DiffExtension` (constraint 1). The processor and its `CombinedDiffViewer` are owned by the plugin's diff dialog, so iteration is inherently scoped to that view.
- For each block, the owning child viewer MUST be obtained from the combined viewer by its `CombinedBlockId`, and its text editors retrieved through the concrete viewer's editor accessor (e.g. `UnifiedDiffViewer.getEditors()`); a block whose viewer exposes no editor MUST be skipped (constraint 4).
- Gutter icons MUST be added as `RangeHighlighter`s in each editor's `MarkupModel` with a `GutterIconRenderer` carrying the icon, tooltip, and a click `AnAction`; the renderer MUST implement `equals`/`hashCode` so identical decorations de-duplicate.
- Each decoration MUST resolve its originating node by mapping the block's `CombinedPathBlockId` repo-relative path back to the `NodePath` whose `File` segment matches (constraint 2); the click action MUST express node-scoped intent (e.g. select/navigate the node in the graph) through ide-layer callbacks, not by reaching into the rendering surface state directly (constraint 3).
- All highlighter and renderer additions MUST run on the EDT and MUST be released with the diff dialog's `Disposable`, alongside the processor disposal already performed when the dialog closes.

## Rationale
- The global `DiffExtension` EP is the documented hook, but it fires for every diff in the IDE; satisfying constraint 1 through it would require tagging each `DiffRequest`/producer with a marker in user data and guarding the extension on that marker. The processor-scoped walk reaches the same editors with strictly local blast radius and no global registration, so it is preferred; the EP route is recorded as the rejected alternative because its project-wide scope is a liability the marker-guard only patches over.
- Correlating by the block's `FilePath` (already the combined-block key) to `NodePath.fileSegment()` reuses identities analysis already mints (ADR-005), avoiding a second source of truth for "which node is this file".
- Keeping decoration in the ide layer and expressing click intent through callbacks preserves ADR-001 layering and the ADR-011 git-free rendering surface: the Compose canvas continues to see only `GraphPanelState` and callbacks, never editors or VCS changes.
- `GutterIconRenderer` on a `RangeHighlighter` is the same primitive the platform uses for breakpoints and bookmarks, so the affordance matches user expectation and inherits gutter hit-testing, tooltips, and click handling instead of hand-built Swing.

## Notes
- Open for this draft: the concrete decoration set and its semantics — candidate icons/actions include "reveal this file's node in the graph", "this line belongs to a changed symbol", and node-level markers at a block's first line. These are deferred until the diff inspection interaction is settled.
- Not yet decided whether decoration also applies when the plugin later opens the diff as an editor tab rather than a dialog; the processor-scoped approach generalizes to both because it depends on the owned `CombinedDiffComponentProcessor`, not on the host window.
- The combined diff view itself (node-and-descendants change set, working dir vs merge base of HEAD and the selected branch) is implemented in the ide layer but is not yet captured by its own ADR; if that view's decisions need to be durable, they warrant a separate record this one can reference.
