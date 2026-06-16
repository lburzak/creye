# ADR-004 Changed Symbol Detection

## Problem & Context
- Analysis must identify changed Kotlin symbols at file, class, function, and property granularity where feasible.
- Git diffs identify changed lines or hunks, while Kotlin PSI identifies declarations and source ranges.
- Some edits affect a declaration directly, while others affect file-level structure, imports, annotations, modifiers, or containing declarations.
- Declaration mapping may be ambiguous for malformed code, incomplete indexing, or unresolved project state.
## Constraints
1. The implementation MUST operate on the changed Kotlin files identified per ADR-003, mapping each to declarations rather than re-deriving the changed-file set.
2. The implementation MUST identify changed class, function, and property declarations when feasible.
3. The implementation MUST preserve file-level change information when declaration-level mapping is ambiguous.
4. The implementation MUST distinguish changed declarations from surrounding contextual declarations (the containing or adjacent declarations a hunk touches without changing) where feasible.
5. The implementation MUST expose diagnostics for changed hunks that cannot be mapped to declarations.

## Decision
- Symbol detection MUST consume the changed Kotlin file records produced by ADR-003 — including file state, two-sided ranges, and baseline and current content — and MUST NOT re-derive the changed-file set or read git directly.
- Each declaration's own range MUST be its source range minus the union of its direct child declarations' source ranges. A declaration MUST be reported changed when a changed hunk intersects its own range.
- The file node MUST be treated as the root declaration; its own range covers imports, the package directive, file annotations, and top-level gaps. A hunk intersecting it MUST be reported as a deterministic file-level change, not a diagnostic.
- Declarations whose own range is not intersected but that contain or adjoin a changed declaration MUST be reported as contextual, not changed (constraint 4).
- The own-range rule MUST be applied symmetrically: additions and modifications MUST be detected against current PSI over current-side ranges; deletions MUST be detected against baseline PSI over baseline-side removed ranges.
- Each reported change MUST carry a change kind derived from the ADR-003 file state and hunk side: added, modified, or deleted. Deleted declarations MUST be listed individually, not collapsed into their parent or file.
- File states MUST map as follows: modified files map both sides per the own-range rule; added files mark every current declaration and the file node added; deleted files enumerate every baseline declaration and the file node deleted; renamed files with unchanged content report the file moved with both paths and no declaration change; renamed files with changed content apply the modified mapping and report the file moved.
- When the PSI needed for a hunk is malformed or unparseable, detection MUST fall back to a file-level change (constraint 3). When a hunk cannot be located in any PSI range on either side, detection MUST emit a diagnostic (constraint 5). This declaration → file → diagnostic ladder is the resolution of every "where feasible" hedge in constraints 2 and 4.
- Detection MUST NOT filter changes by significance; comment- and whitespace-only edits MUST mark the enclosing own-range declaration changed. Detection MAY tag a change with whether all intersecting tokens were comment or whitespace, as non-dropping metadata for consumers that choose to filter.
- Detected declarations MUST be bridged to ADR-005 node identity so downstream phases refer to the same nodes.
- Diagnostics MUST be reported as changed-symbol-detection diagnostics per ADR-010.

## Rationale
- Consuming ADR-003 records instead of re-deriving the changed-file set keeps git comparison and changed symbol detection separated (constraint 1), and reading content rather than git keeps ADR-004 out of the git boundary that ADR-003 owns.
- The own-range subtraction rule is what lets a hunk be attributed to the smallest enclosing declaration without marking every ancestor changed, because a Kotlin declaration's source range encloses its children. This directly satisfies the changed-versus-contextual distinction (constraint 4) and gives "changed declaration" a precise meaning (constraint 2).
- Treating the file as the root declaration unifies file-level structure (imports, package directive, file annotations) with the same rule, so structural edits are deterministic file-level changes rather than ambiguous or undiagnosed ones, which keeps constraint 3 about genuine ambiguity and constraint 5 about genuinely unmappable hunks.
- Detecting deletions against baseline PSI lists the symbols that disappeared, which downstream impact analysis needs; collapsing deletions to a parent would satisfy the letter of constraint 2 while losing the very symbols a change set must surface. This is why ADR-003 was extended to expose baseline content and two-sided ranges.
- Carrying an explicit change kind prevents downstream phases from inferring add, modify, or delete from the presence or absence of a node, mirroring the file-state explicitness ADR-003 already provides.
- The declaration → file → diagnostic ladder gives every "where feasible" hedge in constraints 2 and 4 a concrete, ordered fallback, so degradation is predictable instead of silent.
- Declining to filter by significance keeps ADR-004 a detector rather than a judge; significance varies by consumer, and a filter here would discard information no later phase can recover, while risking the more expensive under-reporting error. The optional metadata tag preserves the choice for consumers without losing data.
- Bridging to ADR-005 identity at the boundary, as ADR-002 does for the project model, prevents later phases from inventing separate identities for the same changed declaration.

## Notes
Rejected alternatives, kept so they are not relitigated:
- **Collapsing deletions to the parent or file** instead of listing deleted declarations. Rejected because downstream impact analysis needs the symbols that disappeared; parent-level attribution loses them. Listing deletions is what forced baseline PSI, and therefore the ADR-003 contract extension.
- **ADR-004 reading baseline blobs from git directly** to obtain baseline PSI. Rejected because it would make ADR-004 interact with git and break the ADR-003 boundary (ADR-003 constraint 2). ADR-003 exposes baseline content instead, and ADR-004 builds PSI from raw content.
- **Filtering comment- and whitespace-only edits** at detection. Rejected because significance is a consumer's call, token-level filtering spends analysis to discard data, and dropping risks under-reporting; an optional metadata tag preserves the choice without losing data.

Downstream-contract amendment (raised by ADR-015/ADR-016/ADR-019/ADR-022):
- Detection already computes each declaration's source range (`DeclNode.fullRange`/`ownRanges`), but the domain change record (`ChangedDeclaration`) currently drops it, keeping only identity, kind, file path, and display name. The combined-diff decoration consumers (ADR-015 range-anchored gutter icons, ADR-016 content highlighting, ADR-019 approval) need a node's in-file line range in the ide layer. The detection output contract is therefore extended to **retain the current-side line range on the exported changed-declaration record**, so the range survives the domain boundary for ide consumers. This carries no range into the rendering projection (`VisibleNode`); the Compose surface stays range-free (ADR-009).
- ADR-022 approval change-detection fingerprints the diff a node owns on **both sides** (before→after), to bind approval to the change rather than the resulting code. The exported record is therefore further extended to **also retain the baseline-side range** of a changed declaration, alongside the current-side range, so the before-side content is reachable at materialization. This surfaces data detection already computes — baseline PSI is already built here for deletion detection — so it is a contract extension, not a new resolution pass. It supersedes the earlier assumption that baseline-side ranges are not required by consumers: that holds for ADR-016 highlighting (after side only) but not for ADR-022 fingerprinting. The Compose surface still stays range-free (ADR-009); only ide/domain consumers see the two-sided range.
