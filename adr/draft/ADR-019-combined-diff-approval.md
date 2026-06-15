# ADR-019 Combined Diff Approval

## Problem & Context
- REQUIREMENTS requires the combined diff to let the reviewer approve directly from the diff: a file-level approval control per file, a gutter icon toggling approval for classes/symbols (green circle approved, red circle unapproved, click toggles), and highlighting of approved code blocks.
- The combined diff is already the host for two decoration concerns: gutter icons (ADR-015) and content range highlighting (ADR-016). Approval affordances are a third use of the same surface, not a new diff mechanism.
- The approval state, its descendant-validation semantics, and its persistence are owned by ADR-018; this record decides only how approval is shown and toggled inside the diff.
- The diff lives in the ide layer (ADR-001, ADR-015 constraint 3); it must mutate approval through callbacks into the ADR-018 model, never by reaching into persistence or the Compose rendering surface.

## Constraints
1. Approval affordances MUST appear only in the plugin's own combined diff view, reusing the ADR-015 processor-scoped editor walk; they MUST NOT decorate diffs the plugin did not open.
2. A file-level approval control MUST be displayed for each file block.
3. A gutter icon MUST indicate the approval status of classes and symbols: a green circle when validated, a red circle when not, and clicking it MUST toggle approval.
4. Approved code blocks MUST be highlighted within the diff.
5. Toggling approval MUST mutate the ADR-018 model through ide-layer callbacks; it MUST NOT write persistence directly nor reach into the rendering surface (ADR-001, ADR-015 constraint 3).
6. An affordance MUST degrade safely when a block exposes no usable editor (ADR-015 constraint 4).

## Decision
- The class/symbol gutter toggle MUST be built on the ADR-015 gutter mechanism: a `GutterIconRenderer` on a `RangeHighlighter`, added per class/symbol node mapped into the block by the ADR-015 `NodePath`-to-file correlation. The icon MUST be resolved from the node's ADR-018 **effective validation** — green circle when validated, red circle otherwise — and its click `AnAction` MUST invoke a node-scoped approval-toggle callback (constraints 3, 5).
- The file-level control MUST be the file node's approval toggle, realized as the same gutter affordance anchored at the block's first line, writing/removing the file node's own ADR-018 entry (constraint 2).
- Approved-block highlighting MUST be built on the ADR-016 range-highlighting mechanism: a `RangeHighlighter` over the validated node's line range, present iff the node is effectively validated per ADR-018, obeying ADR-016's editor-local markup, side resolution, and above-diff-background layer rules (constraints 1, 4).
- The toggle's write target MUST be the clicked node's **own** ADR-018 entry: clicking adds the entry if absent, removes it if present. The displayed icon and highlight MUST reflect effective validation (own or ancestor entry per ADR-018), so a symbol under an approved file shows as validated even with no own entry.
- All approval decorations MUST be rebuilt from current ADR-018 state when the diff opens and after each toggle, and MUST be released with the diff dialog's `Disposable` alongside the ADR-015/ADR-016 decorations (constraint 6 degradation included).

## Rationale
- Reusing ADR-015 for the gutter toggle and ADR-016 for the block highlight keeps approval on the one scoped, registration-free decoration path those records already justified, instead of introducing a global `DiffExtension` whose project-wide blast radius ADR-015 rejected.
- Resolving icon color from ADR-018 effective validation while writing only the clicked node's own entry keeps the diff a thin view over the model: the container-implies-descendants behavior is the model's, not re-implemented in the diff.
- Expressing toggles as callbacks into ADR-018 preserves the ADR-001/ADR-011 boundary — the diff never touches persistence and the Compose surface never sees editors or VCS state.

## Notes
- Open, shared with ADR-015/ADR-016's deferred decoration vocabulary: the exact rendering of the green/red circle and of the approved-block highlight, and whether the file control is a gutter icon at line one or a block-header affordance.
- Carried from ADR-018's positive-only model: a symbol validated only through an approved ancestor cannot be individually un-approved here. The toggle writes only the clicked node's own entry; there are no negative entries, so revoking such a symbol means removing the ancestor's entry. This is a closed decision, not a deferral.
- The combined diff view itself is still not captured by its own ADR (noted in ADR-015/ADR-016); if recorded, this approval record would reference it as the host.
