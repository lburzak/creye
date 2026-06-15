# ADR-022 Approval Change Detection

## Problem & Context
- REQUIREMENTS requires a node's approval to be invalidated when that node's diffed content changes, and retained when it does not. ADR-018 binds each approval entry to a "content fingerprint" but leaves the fingerprint's definition explicitly open.
- The fingerprint's definition is the whole behavior: it decides what counts as a change, how whitespace and formatting are treated, and what "the node's content" even means for a leaf declaration.
- The inputs already exist from analysis: ADR-004 changed-symbol detection and changed ranges, ADR-016's changed-symbol PSI ranges, and the diffed content of each block. Change detection should be a function over those, not a new analysis pass.
- The fingerprint must be cheap and deterministic enough to recompute on every run and compare against the persisted value, consistent with the ADR-005 segment-value key being PSI-text-derived and stable for unchanged code.

## Constraints
1. The fingerprint MUST be derived only from data analysis already produces (ADR-004 ranges, ADR-016 ranges, diffed content); it MUST NOT trigger a separate semantic resolution pass.
2. The fingerprint MUST be deterministic within and across runs for unchanged node content, so an unchanged node compares equal across runs.
3. A change to the node's diffed content MUST yield a different fingerprint; an unchanged node's content MUST yield the same fingerprint.
4. Fingerprint computation and comparison MUST happen at materialization time and MUST gate retention of the ADR-018 entry: an entry is kept iff its scope (ADR-021), node key (ADR-005), and fingerprint all match.
5. This record governs leaf (own-content) nodes; container aggregation is delegated to ADR-023.

## Decision
- A leaf node's fingerprint MUST be computed over the **post-change diffed content the node owns** — for a symbol, the changed declaration's source text within its ADR-016 range on the working-dir ("after") side; for a leaf file node (ADR-017), the changed-line set the diff attributes to that file.
- The fingerprint MUST be a hash of that content's verbatim text together with the node's ADR-004 change kind, so that a node flipping between change kinds (e.g. modified vs added) invalidates even if text coincides (constraint 3).
- For this draft, the content MUST be taken **verbatim with no semantic normalization**: a whitespace-only or formatting-only edit yields a different fingerprint and therefore invalidates approval. Re-review after a reformat is accepted as the conservative default (see Notes for the normalization alternative).
- Retention MUST be the strict conjunction of three matches: same ADR-021 scope, same ADR-005 node-key serialization, same fingerprint. Any mismatch MUST drop the entry; a dropped entry MUST NOT be silently re-created (constraint 4).
- Change detection MUST consume the ADR-004/ADR-016 ranges and the diff content as given and MUST NOT re-resolve symbols to compute the fingerprint (constraint 1).

## Rationale
- Hashing the verbatim diffed text the node owns is the most direct reading of "the node's diffed content changed": it is exactly the content the reviewer looked at, so it cannot drift from what was reviewed.
- Folding the ADR-004 change kind into the hash closes the case where added vs modified content is textually identical but semantically a different review.
- Choosing verbatim over normalized text for the MVP trades extra re-reviews after reformatting for a definition with zero ambiguity and no formatter model to maintain; normalization can be layered in later without changing the retention contract.
- Making retention a strict three-way conjunction keeps the rule auditable: an approval survives only when scope, identity, and content all agree, and any one of them moving is a defensible reason to ask for re-review.

## Notes
- Open for this draft: whether to normalize away insignificant whitespace/formatting (and possibly comments) so cosmetic edits retain approval. This is the main quality lever; deferred because it needs a defined normalization model and risks retaining approval across edits a reviewer would want to see.
- Open: hash algorithm and collision tolerance — left to implementation, constrained only to be deterministic and stable for identical input.
- Container/aggregate fingerprints are out of scope here and owned by ADR-023, which builds on this leaf definition. Move/rename matching, where the node key changes but content does not, is owned by ADR-024.
