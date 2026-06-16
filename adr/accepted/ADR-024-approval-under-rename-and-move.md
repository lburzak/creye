# ADR-024 Approval Under Rename And Move

## Problem & Context
- The ADR-018 persisted approval key is the ADR-005 segment-value serialization. The file segment uses the file name / module-relative path, and the symbol segment uses the declaration name. So a file move changes the key via its file segment, and a symbol rename changes the key via its symbol segment.
- REQUIREMENTS requires approvals for unchanged content to be retained. A pure move (file relocated, content including the declaration name unchanged) therefore creates a direct conflict: the path-segment key changes, so a naive match drops the approval, violating the retain clause.
- A rename is different in kind from a move. The declaration name IS reviewable content, not just key material: it changes call sites and the meaning a reader assigns the symbol. So a rename is treated as a content change, and the ADR-022 fingerprint MUST include the declaration name. This collapses the "rename" leg — a rename invalidates through ordinary content-change handling and never reaches the migration path. Only a *move* (path changed, name and body identical) can migrate.
- ADR-003 already normalizes changed files with their previous path and file state, and ADR-004 classifies change kinds including moved. The old→new mapping needed to relocate an approval across a move already exists in analysis output.
- ADR-022 defines retention as a strict conjunction of node key and fingerprint. A move breaks the node-key leg through its path segment while leaving the fingerprint intact, so the question is how matching behaves when content (fingerprint) survives but the path key does not.

## Constraints
1. A node whose content — including its declaration name — is unchanged but whose ADR-005 key changed due to a move MUST retain its approval (REQUIREMENTS retain clause).
2. A rename (declaration name changed) MUST be treated as a content change and invalidate per ADR-022; the declaration name is part of the fingerprint. No name-tracking or rename-detection migration path exists.
3. Relocation across a move MUST use the move mapping ADR-003/ADR-004 already produce; it MUST NOT introduce a new mapping mechanism.
4. A move accompanied by a content change MUST invalidate per ADR-022, exactly as any other content change would.
5. Where move confidence or the mapping is uncertain, correctness MUST win over retention: the entry is dropped, never optimistically migrated. A dropped approval costs a re-review; a wrongly migrated one is silent false trust.
6. Relocation MUST NOT weaken ADR-005 keying for the normal (unmoved) case; the node-key match remains the primary path.

## Decision
- Approval matching MUST be staged. First, attempt the ADR-022 two-way match (node key + fingerprint) on the unchanged key. On a key miss, consult the ADR-003/ADR-004 move mapping for that run to find the new key the old key maps to, and attempt the match against the relocated key (constraints 3, 6).
- Migration MUST require a strict conjunction: a **high-confidence** move mapping from old key to new key, AND a matching ADR-022 fingerprint on the relocated key. When both hold, the approval entry MUST be **migrated** to the new key and retained (constraint 1). When either fails — fingerprint differs, mapping absent, or confidence low — the entry MUST be dropped exactly as any content change (constraints 4, 5).
- The fingerprint MUST be the deciding factor on a move: because ADR-022 hashes the node's diffed content on both sides (not its key) and that hash includes the declaration name, a pure move with identical content and name matches and migrates — the before and after text are unchanged, so the fingerprint is stable across the move. A rename changes the declaration name, hence the fingerprint, hence invalidates — and never enters this path, since rename is not in the move mapping. A move that also edits content likewise invalidates on the fingerprint leg. When the node moved, the before-side content the fingerprint needs MUST be taken from the run's own ADR-003/ADR-004 two-sided record, which already pairs the old and new path with their baseline and current content; no separate old-path lookup is introduced (constraint 3).
- A relocation the mapping cannot establish, or establishes only at low confidence, MUST fall back to a clean drop, never to a fuzzy or content-only global match, so an approval can never jump to an unrelated node that merely shares content (constraint 5).

## Rationale
- Staging key-match first and consulting the move/rename mapping only on a miss keeps the common unmoved case on the cheap ADR-005 key path untouched, and pays the mapping lookup only when the key actually moved.
- Letting the content fingerprint decide retention is what reconciles ADR-005 key-keying with the retain clause: the key tells us *which* node, the fingerprint tells us *whether the review still holds*, and a pure move changes the former without the latter.
- Folding the declaration name into the fingerprint makes rename invalidate with no special case. A name is reviewable content — it shifts call sites and reader meaning — so a rename is a change worth re-reviewing, and the policy falls straight out of ADR-022 rather than needing a name-tracking exception.
- Requiring high-confidence mapping AND fingerprint match before migrating chooses correctness over retention. A heuristic move classification can be wrong; a wrong migration silently attaches a review to the wrong node, which is worse than the cheap cost of one re-review. Any doubt drops.
- Reusing the ADR-003/ADR-004 move output avoids a second mapping heuristic and keeps approval migration as accurate as the change classification the rest of the pipeline already trusts.
- Refusing any content-only global fallback prevents the dangerous failure mode where an approval silently attaches to an unrelated node with coincidentally identical text.

## Notes
- Resolved: a *pure rename* (name changed, body and signature identical) is a reviewable change and invalidates. The declaration name is reviewable content folded into the ADR-022 fingerprint, so the rename changes the fingerprint and re-review is required. (Earlier draft retained it; reversed because the name carries intent worth re-reviewing.)
- Resolved: a move reported at low confidence drops rather than migrates optimistically. Migration requires high-confidence mapping AND fingerprint match (constraint 5); correctness wins over retention.
- A combined move + rename (path and name both change, body otherwise identical) invalidates: the rename changes the fingerprint, so even though the move mapping may relocate the path key, the fingerprint leg fails and the entry drops.
- A pure deletion IS approvable under diff-approval: ADR-022 fingerprints the diff a node owns on both sides, and a deletion has before-side content (empty after-side), so a deleted leaf carries a well-defined fingerprint and can be reviewed ("I reviewed this removal"). This supersedes the earlier after-side-only assumption that a removal could not be represented.
- Distinct from the above: a **vanished old key with no mapping target** — a node that is simply absent from the current run, not classified as a deletion in the diff — MUST be a clean drop, since there is no node to re-fingerprint and no relocation to migrate to.
- Depends on ADR-003, ADR-004, ADR-005, ADR-018, ADR-022.
