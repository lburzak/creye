# ADR-011 Configuration

## Problem & Context
- Analysis must compare one configured git branch against the current working directory.
- Branch configuration can live in an action prompt, project setting, persisted plugin state, or another IDE mechanism.
- The selected branch must be clear before analysis starts.
- Configuration mistakes should fail early with useful diagnostics rather than producing an empty or misleading graph.

## Constraints
1. The implementation MUST support configuring exactly one comparison branch.
2. The implementation MUST make the selected branch visible before analysis runs.
3. The implementation MUST validate that the configured branch exists.
4. The implementation MUST stop analysis when branch configuration is missing or invalid.
5. The implementation SHOULD avoid broad configuration surfaces beyond a single comparison branch.

## Decision
- The comparison branch MUST be selected through a single-select dropdown in the editor panel, populated from the repository's current branch list.
- The dropdown selection MUST be the source of truth for the comparison branch; there MUST NOT be a separate configuration file or IDE setting for it.
- The last selection MUST persist in per-project plugin state and MUST be restored when the editor panel opens, so the active comparison branch is visible before analysis runs.
- Analysis MUST NOT run while no branch is selected.
- A persisted branch absent from the current branch list MUST surface a diagnostic and remain unselected, and MUST NOT silently fall back to another branch.

## Rationale
- Populating the dropdown from the live branch list makes an invalid selection unreachable, collapsing the "validate existence" and "stop on invalid configuration" constraints into "you can only pick what exists" rather than validating after the fact.
- The dropdown lives in the editor panel header (a Jewel combo box in the ADR-009 Compose tree), so the selected branch is the on-screen state itself, directly satisfying visibility before analysis.
- The comparison branch feeds graph generation only; no other code path consumes it, so a dropdown as source of truth avoids a broader project-setting layer that nothing else would read (constraint 5).
- Persisting the last selection keeps the branch visible across sessions; the one residual validation case is a persisted branch that was since deleted, handled by surfacing a diagnostic and blocking rather than falling back silently.
