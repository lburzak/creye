# ADR-024 Approval Under Rename and Move

## Problem & Context
- The ADR-018 persisted approval key is the ADR-005 segment-value serialization. The file segment uses the file name / module-relative path, and the symbol segment uses the declaration name. So a file move changes the key, and a symbol rename changes the key, even when the node's content is otherwise identical.
- REQUIREMENTS requires approvals for unchanged content to be retained. A pure move (file relocated, content unchanged) therefore creates a direct conflict: the key changes, so a naive match drops the approval, violating the retain clause.
- ADR-003 already normalizes changed files with their previous path and file state, and ADR-004 classifies change kinds including moved/renamed. The old→new mapping needed to relocate an approval already exists in analysis output.
- ADR-022 defines retention as a strict conjunction of scope, node key, and fingerprint. Move/rename breaks the node-key leg specifically, so the question is how matching behaves when content (fingerprint) survives but the key does not.

## Constraints
1. A node whose content is unchanged but whose ADR-005 key changed due to a move or rename MUST retain its approval (REQUIREMENTS retain clause).
2. Relocation MUST use the move/rename mapping ADR-003/ADR-004 already produce; it MUST NOT introduce a new rename-detection mechanism.
3. A move or rename accompanied by a content change MUST invalidate per ADR-022, exactly as any other content change would.
4. Relocation MUST NOT weaken ADR-005 keying for the normal (unmoved) case; the node-key match remains the primary path.
5. Relocation MUST stay within the comparison scope of ADR-021; an approval MUST NOT migrate across scopes.

## Decision
- Approval matching MUST be staged. First, attempt the ADR-022 three-way match on the unchanged key. On a key miss, consult the ADR-003/ADR-004 move/rename mapping for that run to find the new key the old key maps to, and attempt the match against the relocated key (constraints 2, 4).
- When the mapping relocates an old key to a new key and the ADR-022 fingerprint still matches, the approval entry MUST be **migrated** to the new key and retained (constraint 1). When the fingerprint differs, the entry MUST be dropped exactly as any content change (constraint 3).
- Migration MUST occur only within one ADR-021 comparison-scope partition; the mapping MUST NOT carry an approval into a different scope (constraint 5).
- The fingerprint, not the name, MUST be the deciding factor on a move or rename: because ADR-022 hashes the node's diffed content (not its key), a pure relocation with identical content matches and migrates, while a rename that also edits the declaration invalidates.
- A relocation the mapping cannot establish MUST fall back to a clean drop, never to a fuzzy or content-only global match, so an approval can never jump to an unrelated node that merely shares content.

## Rationale
- Staging key-match first and consulting the move/rename mapping only on a miss keeps the common unmoved case on the cheap ADR-005 key path untouched, and pays the mapping lookup only when the key actually moved.
- Letting the content fingerprint decide retention is what reconciles ADR-005 key-keying with the retain clause: the key tells us *which* node, the fingerprint tells us *whether the review still holds*, and a pure move changes the former without the latter.
- Reusing the ADR-003/ADR-004 move/rename output avoids a second rename-detection heuristic and keeps approval migration as accurate as the change classification the rest of the pipeline already trusts.
- Refusing any content-only global fallback prevents the dangerous failure mode where an approval silently attaches to an unrelated node with coincidentally identical text.

## Notes
- Open for this draft: whether a *pure rename* (name changed, body and signature identical) should retain approval or be treated as a reviewable change. This record retains it, because content is unchanged and the requirement is content-based; the opposing view is that a rename can carry intent worth re-reviewing. Flagged for discussion.
- Open: behavior when analysis reports a move/rename with low confidence — whether to migrate optimistically or drop. Left to the change-classification confidence model, not decided here.
- Depends on ADR-003, ADR-004, ADR-005, ADR-018, ADR-021, ADR-022.
